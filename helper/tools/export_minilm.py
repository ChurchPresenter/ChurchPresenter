"""Exports Wick's sentence encoder, all-MiniLM-L6-v2, into the one file the helper bundles.

    python3 helper/tools/export_minilm.py            # writes the model, the vocabulary and the test fixtures

Needs `numpy` and `tokenizers`. Re-running overwrites `helper/src/main/resources/wick/minilm-l6.bin`
and `wick/vocab.txt` in place: there is only ever one model in the tree.

Every weight matrix is stored as int8 with one fp32 scale per row; biases, LayerNorm and the position and
token-type embeddings stay fp32. The file is little-endian:

    "WICKML01"  u32 tensor count
    per tensor: u16 name length, UTF-8 name, u8 kind (0 = fp32, 1 = int8 rows), u8 rank, u32 dims...,
                kind 0: fp32 values; kind 1: one fp32 scale per row, then the int8 values
"""
import hashlib
import json
import struct
import sys
import urllib.request
from math import erf
from pathlib import Path

import numpy as np
from tokenizers import Tokenizer

REPO = "sentence-transformers/all-MiniLM-L6-v2"
REVISION = "1110a243fdf4706b3f48f1d95db1a4f5529b4d41"
MAX_TOKENS = 128
LAYERS = 6
HEADS = 12
HIDDEN = 384

ROOT = Path(__file__).resolve().parents[1]
MODEL_OUT = ROOT / "src/main/resources/wick/minilm-l6.bin"
VOCAB_OUT = ROOT / "src/main/resources/wick/vocab.txt"
FIXTURE_OUT = ROOT / "src/test/resources/wick/reference.tsv"

# Sentences the Kotlin port is checked against: plain requests, accents, punctuation, a typo, a long one.
FIXTURE_SENTENCES = [
    "where do i add a song",
    "make the song background blue",
    "nothing shows on the second monitor",
    "the words are too small to read from the back",
    "clera the screen",
    "Show John 3:16!",
    "Café Crème, naïve résumé",
    "how do I put a timer on the stage screen?",
    "unbelievably-long hyphenated, comma-separated; semicolon: words",
    "a",
    "where's the order of service",
    "put guitar chords above the lyrics",
]


def fetch(name: str) -> bytes:
    url = f"https://huggingface.co/{REPO}/resolve/{REVISION}/{name}"
    with urllib.request.urlopen(url) as response:
        return response.read()


def read_safetensors(blob: bytes) -> dict:
    header_len = struct.unpack("<Q", blob[:8])[0]
    header = json.loads(blob[8 : 8 + header_len])
    tensors = {}
    for name, meta in header.items():
        # position_ids is a buffer of 0..511, not a weight.
        if name == "__metadata__" or name.endswith("position_ids"):
            continue
        assert meta["dtype"] == "F32", (name, meta["dtype"])
        start, end = meta["data_offsets"]
        data = np.frombuffer(blob[8 + header_len + start : 8 + header_len + end], dtype="<f4")
        tensors[name.removeprefix("bert.")] = data.reshape(meta["shape"]).copy()
    return tensors


def quantize_rows(matrix: np.ndarray):
    scale = np.abs(matrix).max(axis=1) / 127.0
    scale[scale == 0] = 1.0
    q = np.clip(np.round(matrix / scale[:, None]), -127, 127).astype(np.int8)
    return scale.astype("<f4"), q


def stored_tensors(weights: dict):
    """The tensors the encoder reads, in the order it reads them, as (name, kind, array)."""
    def fp32(name, array=None):
        return name, 0, (weights[name] if array is None else array).astype("<f4")

    def int8(name):
        return name, 1, weights[name]

    yield int8("embeddings.word_embeddings.weight")
    yield fp32("embeddings.position_embeddings.weight", weights["embeddings.position_embeddings.weight"][:MAX_TOKENS])
    yield fp32("embeddings.token_type_embeddings.weight", weights["embeddings.token_type_embeddings.weight"][:1])
    yield fp32("embeddings.LayerNorm.weight")
    yield fp32("embeddings.LayerNorm.bias")
    for layer in range(LAYERS):
        p = f"encoder.layer.{layer}."
        for dense in ("attention.self.query", "attention.self.key", "attention.self.value",
                      "attention.output.dense", "intermediate.dense", "output.dense"):
            yield int8(p + dense + ".weight")
            yield fp32(p + dense + ".bias")
        for norm in ("attention.output.LayerNorm", "output.LayerNorm"):
            yield fp32(p + norm + ".weight")
            yield fp32(p + norm + ".bias")


def write_model(weights: dict) -> dict:
    """Writes the bundled file and returns the weights as the app will see them (int8 dequantized)."""
    seen = {}
    out = bytearray(b"WICKML01")
    tensors = list(stored_tensors(weights))
    out += struct.pack("<I", len(tensors))
    for name, kind, array in tensors:
        encoded = name.encode()
        out += struct.pack("<H", len(encoded)) + encoded
        out += struct.pack("<BB", kind, array.ndim)
        out += struct.pack(f"<{array.ndim}I", *array.shape)
        if kind == 0:
            out += array.tobytes()
            seen[name] = array
        else:
            scale, q = quantize_rows(array)
            out += scale.tobytes() + q.tobytes()
            seen[name] = q.astype(np.float32) * scale[:, None]
    MODEL_OUT.parent.mkdir(parents=True, exist_ok=True)
    MODEL_OUT.write_bytes(bytes(out))
    return seen


def layer_norm(x, w, b):
    mean = x.mean(axis=-1, keepdims=True)
    var = ((x - mean) ** 2).mean(axis=-1, keepdims=True)
    return (x - mean) / np.sqrt(var + 1e-12) * w + b


_gelu = np.frompyfunc(lambda v: 0.5 * v * (1.0 + erf(v / 2 ** 0.5)), 1, 1)


def encode(w: dict, ids: list) -> np.ndarray:
    """The reference forward pass: what the Kotlin encoder must reproduce."""
    x = (w["embeddings.word_embeddings.weight"][ids]
         + w["embeddings.position_embeddings.weight"][: len(ids)]
         + w["embeddings.token_type_embeddings.weight"][0])
    x = layer_norm(x, w["embeddings.LayerNorm.weight"], w["embeddings.LayerNorm.bias"])
    head = HIDDEN // HEADS
    for layer in range(LAYERS):
        p = f"encoder.layer.{layer}."
        dense = lambda t, name: t @ w[p + name + ".weight"].T + w[p + name + ".bias"]
        q, k, v = (dense(x, "attention.self." + n).reshape(len(ids), HEADS, head).transpose(1, 0, 2)
                   for n in ("query", "key", "value"))
        scores = q @ k.transpose(0, 2, 1) / np.sqrt(head)
        scores = np.exp(scores - scores.max(axis=-1, keepdims=True))
        scores /= scores.sum(axis=-1, keepdims=True)
        context = (scores @ v).transpose(1, 0, 2).reshape(len(ids), HIDDEN)
        x = layer_norm(dense(context, "attention.output.dense") + x,
                       w[p + "attention.output.LayerNorm.weight"], w[p + "attention.output.LayerNorm.bias"])
        hidden = _gelu(dense(x, "intermediate.dense")).astype(np.float32)
        x = layer_norm(dense(hidden, "output.dense") + x,
                       w[p + "output.LayerNorm.weight"], w[p + "output.LayerNorm.bias"])
    pooled = x.mean(axis=0)
    return pooled / np.linalg.norm(pooled)


def main() -> None:
    weights = read_safetensors(fetch("model.safetensors"))
    vocab = fetch("vocab.txt")
    tokenizer = Tokenizer.from_str(fetch("tokenizer.json").decode())
    tokenizer.no_padding()
    tokenizer.enable_truncation(MAX_TOKENS)

    stored = write_model(weights)
    VOCAB_OUT.write_bytes(vocab)

    FIXTURE_OUT.parent.mkdir(parents=True, exist_ok=True)
    rows = []
    worst = 1.0
    for sentence in FIXTURE_SENTENCES:
        ids = tokenizer.encode(sentence).ids
        as_shipped = encode(stored, ids)
        full = encode(weights, ids)
        worst = min(worst, float(as_shipped @ full))
        vector = " ".join(f"{v:.6f}" for v in as_shipped)
        rows.append(f"{sentence}\t{' '.join(map(str, ids))}\t{vector}")
    FIXTURE_OUT.write_text("\n".join(rows) + "\n")

    digest = hashlib.sha256(MODEL_OUT.read_bytes()).hexdigest()
    print(f"{REPO}@{REVISION}")
    print(f"wrote {MODEL_OUT.relative_to(ROOT)} ({MODEL_OUT.stat().st_size / 1e6:.1f} MB) sha256 {digest}")
    print(f"int8 vs fp32, worst cosine over the fixtures: {worst:.5f}")


if __name__ == "__main__":
    sys.exit(main())

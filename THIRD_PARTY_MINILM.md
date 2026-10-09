# all-MiniLM-L6-v2, as bundled with ChurchPresenter

Wick, the in-app helper, reads requests its rules do not understand with a small sentence model,
**all-MiniLM-L6-v2**, run inside the application in plain Kotlin. ChurchPresenter ships the model so
that nothing is downloaded at run time. This file is the disclosure that shipping it requires.

## What is included

- `helper/src/main/resources/wick/minilm-l6.bin` — the model's weights
- `helper/src/main/resources/wick/vocab.txt` — its WordPiece vocabulary, unmodified
- `helper/src/main/resources/wick/LICENSE-all-MiniLM-L6-v2.txt` — the Apache License 2.0

Both files are written by [`helper/tools/export_minilm.py`](helper/tools/export_minilm.py) from
`sentence-transformers/all-MiniLM-L6-v2` at revision `1110a243fdf4706b3f48f1d95db1a4f5529b4d41`.

## Changes made

The weights are **modified**: every weight matrix is quantized to 8-bit integers with one scale per
row; biases and layer norms stay 32-bit; the position embeddings are cut to the first 128 positions
and the token-type embedding to the one row a single sentence uses; the unused pooler is left out;
and everything is stored in ChurchPresenter's own file format (described in the export script). The
vocabulary is unchanged.

## Licence

The model is released under the **Apache License, Version 2.0** by its authors (the
sentence-transformers project, after Microsoft's MiniLM). The full licence is shipped beside the
model as `LICENSE-all-MiniLM-L6-v2.txt`. Apache 2.0 is compatible with ChurchPresenter's own GNU GPL v3.

## Source

<https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2>

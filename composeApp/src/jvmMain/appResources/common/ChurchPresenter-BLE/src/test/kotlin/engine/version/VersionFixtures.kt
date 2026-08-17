package engine.version

import engine.bible.Script

/** A [VersionCorpus] built from literal verse text — no `.spb` files, no filesystem. */
class MapVersionCorpus(private val byCode: Map<String, List<VersionCandidate>>) : VersionCorpus {
    override fun rendering(code: String): List<VersionCandidate> = byCode[code].orEmpty()
    override val labels: List<String> = byCode.values.flatten().map { it.label }.distinct()
}

fun candidate(label: String, text: String, script: Script = Script.LATIN) =
    VersionCandidate(id = "ENG_$label", label = label, script = script, text = text)

// Matthew 18:13, verbatim from the two modules on the dev machine — the case that prompted the
// feature. The discriminating wording is "if it turns out that he finds it" vs "if so be that he
// find it".
const val MATT_18_13 = "B040C018V013"

const val KJV_MATT_18_13 =
    "And if so be that he find it, verily I say unto you, he rejoiceth more of that sheep, " +
        "than of the ninety and nine which went not astray."

const val NASB_MATT_18_13 =
    "If it turns out that he finds it, truly I say to you, he rejoices over it more than over " +
        "the ninety-nine which have not gone astray."

// Matthew 18:14 in both, for multi-verse accumulation.
const val KJV_MATT_18_14 =
    "Even so it is not the will of your Father which is in heaven, that one of these little ones should perish."

const val NASB_MATT_18_14 =
    "So it is not the will of your Father who is in heaven that one of these little ones perish."

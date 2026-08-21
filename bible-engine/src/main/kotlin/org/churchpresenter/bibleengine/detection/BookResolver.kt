package org.churchpresenter.bibleengine.detection

object BookResolver {

    val CANONICAL_NAMES: Map<Int, String> = mapOf(
        1 to "Genesis", 2 to "Exodus", 3 to "Leviticus", 4 to "Numbers",
        5 to "Deuteronomy", 6 to "Joshua", 7 to "Judges", 8 to "Ruth",
        9 to "1 Samuel", 10 to "2 Samuel", 11 to "1 Kings", 12 to "2 Kings",
        13 to "1 Chronicles", 14 to "2 Chronicles", 15 to "Ezra", 16 to "Nehemiah",
        17 to "Esther", 18 to "Job", 19 to "Psalms", 20 to "Proverbs",
        21 to "Ecclesiastes", 22 to "Song of Solomon", 23 to "Isaiah", 24 to "Jeremiah",
        25 to "Lamentations", 26 to "Ezekiel", 27 to "Daniel", 28 to "Hosea",
        29 to "Joel", 30 to "Amos", 31 to "Obadiah", 32 to "Jonah",
        33 to "Micah", 34 to "Nahum", 35 to "Habakkuk", 36 to "Zephaniah",
        37 to "Haggai", 38 to "Zechariah", 39 to "Malachi",
        40 to "Matthew", 41 to "Mark", 42 to "Luke", 43 to "John",
        44 to "Acts", 45 to "Romans", 46 to "1 Corinthians", 47 to "2 Corinthians",
        48 to "Galatians", 49 to "Ephesians", 50 to "Philippians", 51 to "Colossians",
        52 to "1 Thessalonians", 53 to "2 Thessalonians",
        54 to "1 Timothy", 55 to "2 Timothy",
        56 to "Titus", 57 to "Philemon", 58 to "Hebrews", 59 to "James",
        60 to "1 Peter", 61 to "2 Peter",
        62 to "1 John", 63 to "2 John", 64 to "3 John",
        65 to "Jude", 66 to "Revelation",
    )

    // Apostrophe-like characters that can appear inside book names (typewriter, typographic,
    // modifier letter, backtick). See the ALIASES doc note for why variants are registered.
    private val APOSTROPHES = charArrayOf('\'', '’', 'ʼ', '`')

    private fun apostropheVariants(name: String): List<String> {
        if (name.none { it in APOSTROPHES }) return emptyList()
        var spaced = name
        var deleted = name
        for (ch in APOSTROPHES) {
            spaced = spaced.replace(ch, ' ')
            deleted = deleted.replace(ch.toString(), "")
        }
        return listOf(spaced.trim().replace(Regex("\\s+"), " "), deleted).filter { it.isNotEmpty() && it != name }
    }

    val ALIASES: Map<String, Int> = buildMap {
        // Aliases containing an apostrophe (Ukrainian "об'явлення" etc.) also register their
        // apostrophe→space and apostrophe-deleted forms: the tokenizer splits apostrophes to
        // whitespace (so the greedy multi-token join sees "об явлення"), and STT sometimes
        // drops them entirely ("обявлення"). Without the variants such aliases can never match.
        fun add(num: Int, vararg names: String) = names.forEach { name ->
            put(name, num)
            apostropheVariants(name).forEach { putIfAbsent(it, num) }
        }

        // ── English ──────────────────────────────────────────────────────────
        add(1, "genesis", "gen", "ge", "gn")
        add(2, "exodus", "exod", "exo", "ex")
        add(3, "leviticus", "lev", "le", "lv")
        add(4, "numbers", "num", "numb", "nu", "nm")
        add(5, "deuteronomy", "deut", "deu", "dt")
        add(6, "joshua", "josh", "jos", "jsh")
        add(7, "judges", "judg", "jdg", "jg", "jgs")
        add(8, "ruth", "rth", "ru")
        add(9, "1 samuel", "1samuel", "1sam", "1sa", "1s", "first samuel")
        add(10, "2 samuel", "2samuel", "2sam", "2sa", "2s", "second samuel")
        add(11, "1 kings", "1kings", "1kgs", "1ki", "1k", "first kings")
        add(12, "2 kings", "2kings", "2kgs", "2ki", "2k", "second kings")
        add(13, "1 chronicles", "1chronicles", "1chr", "1ch", "first chronicles")
        add(14, "2 chronicles", "2chronicles", "2chr", "2ch", "second chronicles")
        add(15, "ezra", "ezr")
        add(16, "nehemiah", "neh", "ne")
        add(17, "esther", "esth", "est")
        add(18, "job", "jb")
        add(19, "psalms", "psalm", "psa", "ps", "pss")
        add(20, "proverbs", "prov", "pro", "prv", "pr")
        add(21, "ecclesiastes", "eccl", "eccles", "ecc", "qoh")
        add(22, "song of solomon", "song of songs", "song", "sos", "songs", "canticles", "cant")
        add(23, "isaiah", "isa")
        add(24, "jeremiah", "jer", "je")
        // "lamentations of jeremiah" is how the live machine translation renders «Плач Иеремии».
        // Without the compound the greedy join takes "lamentations",
        // then "jeremiah" as a second book atom, and interpret() keeps the LAST book before the
        // numbers — so the English track named Jeremiah for a Lamentations sermon.
        add(25, "lamentations", "lamentations of jeremiah", "lam", "la")
        add(26, "ezekiel", "ezek", "eze", "ezk")
        add(27, "daniel", "dan", "da", "dn")
        add(28, "hosea", "hos", "ho")
        add(29, "joel", "jl")
        add(30, "amos", "am")
        add(31, "obadiah", "obad", "ob")
        add(32, "jonah", "jon", "jnh")
        add(33, "micah", "mic", "mi")
        add(34, "nahum", "nah", "na")
        add(35, "habakkuk", "hab", "hb")
        add(36, "zephaniah", "zeph", "zep", "zp")
        add(37, "haggai", "hag", "hg")
        add(38, "zechariah", "zech", "zec", "zc")
        add(39, "malachi", "mal", "ml")
        add(40, "matthew", "matt", "mat", "mt")
        add(41, "mark", "mrk", "mk", "mr")
        add(42, "luke", "luk", "lk")
        add(43, "john", "jn", "joh")
        add(44, "acts", "act", "ac")
        add(45, "romans", "rom", "ro", "rm")
        add(46, "1 corinthians", "1corinthians", "1cor", "1co", "first corinthians")
        add(47, "2 corinthians", "2corinthians", "2cor", "2co", "second corinthians")
        add(48, "galatians", "gal", "ga")
        add(49, "ephesians", "eph", "ep")
        add(50, "philippians", "phil", "php", "pp")
        add(51, "colossians", "col")
        add(52, "1 thessalonians", "1thessalonians", "1thess", "1th", "first thessalonians")
        add(53, "2 thessalonians", "2thessalonians", "2thess", "2th", "second thessalonians")
        add(54, "1 timothy", "1timothy", "1tim", "1ti", "first timothy")
        add(55, "2 timothy", "2timothy", "2tim", "2ti", "second timothy")
        add(56, "titus", "tit")
        add(57, "philemon", "philem", "phlm", "phm")
        add(58, "hebrews", "heb", "he")
        add(59, "james", "jas", "jm")
        add(60, "1 peter", "1peter", "1pet", "1pe", "1pt", "first peter")
        add(61, "2 peter", "2peter", "2pet", "2pe", "2pt", "second peter")
        add(62, "1 john", "1john", "1jn", "1jo", "first john")
        add(63, "2 john", "2john", "2jn", "2jo", "second john")
        add(64, "3 john", "3john", "3jn", "3jo", "third john")
        add(65, "jude", "jud")
        add(66, "revelation", "rev", "re", "rv", "apocalypse", "apoc")

        // ── Russian / Church Slavonic ─────────────────────────────────────────
        add(1, "бытие", "быт")
        add(2, "исход", "исх")
        add(3, "левит", "лев")
        add(4, "числа", "чис")
        add(5, "второзаконие", "втор")
        add(6, "иисуса навина", "иис.нав.", "нав")
        add(7, "судей", "суд")
        // Genitive too: "Книга Руфи" is the book's ordinary Russian title, and at 4 chars it
        // inherits the short-alias corroboration gate, so it stays refused in bare prose.
        add(8, "руфь", "руфи")
        add(9, "1 царств", "1-я царств", "1цар", "1 царство")
        add(10, "2 царств", "2-я царств", "2цар", "2 царство")
        add(11, "3 царств", "3-я царств", "3цар", "3 царство")
        add(12, "4 царств", "4-я царств", "4цар", "4 царство")
        add(13, "1 паралипоменон", "1пар")
        add(14, "2 паралипоменон", "2пар")
        add(15, "ездра", "езд")
        add(16, "неемия", "неем")
        add(17, "есфирь", "есф")
        add(18, "иов", "иова")   // genitive "Книга Иова"; ≤4 chars, so still gated in prose
        add(19, "псалтирь", "псалтырь", "псалом", "пс")
        add(20, "притчи", "прит", "при")
        add(21, "екклесиаст", "екк")
        add(22, "песня песней", "песни песней", "пес")
        add(23, "исаия", "исайя", "ис")
        add(24, "иеремия", "иер")
        add(25, "плач иеремии", "плач")
        // "иезекииль" is the correct Synodal spelling; the table carried only the one-и typo
        // "иезекиль", so neither "Иезекииль" nor "Иезекииля" resolved at all until this was added.
        // Both spellings are kept — the STT produces either.
        add(26, "иезекииль", "иезекиль", "иез")
        add(27, "даниил", "дан")
        add(28, "осия", "ос")
        add(29, "иоиль")
        add(30, "амос")
        add(31, "авдий", "авд")
        add(32, "иона", "ионы")  // genitive "Книга Ионы"; ≤4 chars, so still gated in prose
        add(33, "михей", "мих")
        add(34, "наум")
        add(35, "аввакум", "авв")
        add(36, "софония", "соф")
        add(37, "аггей", "агг")
        add(38, "захария", "зах")
        add(39, "малахия", "мал")
        add(40, "матфей", "мф", "от матфея")
        add(41, "марк", "мк", "от марка")
        add(42, "лука", "лк", "от луки")
        add(43, "иоанна", "иоанн", "ин", "от иоанна")
        add(44, "деяния", "деян")
        add(45, "римлянам", "рим")
        add(46, "1 коринфянам", "1кор")
        add(47, "2 коринфянам", "2кор")
        add(48, "галатам", "гал")
        add(49, "ефесянам", "еф")
        add(50, "филиппийцам", "флп")
        add(51, "колоссянам", "кол")
        add(52, "1 фессалоникийцам", "1фес")
        add(53, "2 фессалоникийцам", "2фес")
        add(54, "1 тимофею", "1тим")
        add(55, "2 тимофею", "2тим")
        add(56, "титу", "тита", "тит")  // "Послание к Титу" / "Послание Тита"
        add(57, "филимону", "флм")
        add(58, "евреям", "евр")
        add(59, "иакова", "иак")
        add(60, "1 петра", "1пет")
        add(61, "2 петра", "2пет")
        add(62, "1 иоанна", "1ин")
        add(63, "2 иоанна", "2ин")
        add(64, "3 иоанна", "3ин")
        add(65, "иуды", "иуд")
        add(66, "откровение", "откр", "апокалипсис")

        // ── German (Deutsch) ─────────────────────────────────────────────────
        add(1, "1. mose", "1 mose", "1mose")
        add(2, "2. mose", "2 mose", "2mose")
        add(3, "3. mose", "3 mose", "3mose")
        add(4, "4. mose", "4 mose", "4mose")
        add(5, "5. mose", "5 mose", "5mose")
        add(6, "josua")
        add(7, "richter", "ri")
        add(8, "rut")
        add(9, "1. samuel", "1. sam")
        add(10, "2. samuel", "2. sam")
        add(11, "1. könige", "1 könige", "1könige", "1. koenige", "1 koenige", "1koenige")
        add(12, "2. könige", "2 könige", "2könige", "2. koenige", "2 koenige", "2koenige")
        add(13, "1. chronik", "1 chronik", "1chronik")
        add(14, "2. chronik", "2 chronik", "2chronik")
        add(15, "esra")
        add(16, "nehemia")
        add(17, "ester")
        add(18, "hiob", "hi")
        add(19, "psalmen")
        add(20, "sprichwörter", "sprichworter", "spr")
        add(21, "prediger", "pred", "koh")
        add(22, "hoheslied", "hohelied", "hl")
        add(23, "jesaja", "jes")
        add(24, "jeremia")
        add(25, "klagelieder", "kgl")
        add(26, "hesekiel", "hes", "hez")
        add(28, "hosea")
        add(31, "obadja")
        add(32, "jona")
        add(33, "micha")
        add(35, "habakuk")
        add(36, "zefanja", "zef")
        add(38, "sacharja", "sach")
        add(39, "maleachi")
        add(40, "matthäus", "matthaus", "matth")
        add(41, "markus")
        add(42, "lukas")
        add(43, "johannes")
        add(44, "apostelgeschichte", "apg")
        add(45, "römer", "romer", "röm")
        add(46, "1. korinther", "1 korinther", "1korinther")
        add(47, "2. korinther", "2 korinther", "2korinther")
        add(48, "galater")
        add(49, "epheser")
        add(50, "philipper")
        add(51, "kolosser")
        add(52, "1. thessalonicher", "1 thessalonicher", "1thessalonicher")
        add(53, "2. thessalonicher", "2 thessalonicher", "2thessalonicher")
        add(54, "1. timotheus", "1 timotheus", "1timotheus")
        add(55, "2. timotheus", "2 timotheus", "2timotheus")
        add(57, "philemon")
        add(58, "hebräer", "hebraer", "hebr")
        add(59, "jakobus", "jak")
        add(60, "1. petrus", "1 petrus", "1petrus")
        add(61, "2. petrus", "2 petrus", "2petrus")
        add(62, "1. johannes", "1 johannes", "1johannes", "1joh")
        add(63, "2. johannes", "2 johannes", "2johannes", "2joh")
        add(64, "3. johannes", "3 johannes", "3johannes", "3joh")
        add(65, "judas")
        add(66, "offenbarung", "offb")

        // ── French (Français) ─────────────────────────────────────────────────
        add(1, "genèse", "genese")
        add(2, "exode")
        add(3, "lévitique", "levitique", "lév")
        add(4, "nombres", "nb")
        add(5, "deutéronome", "deuteronome")
        add(6, "josué", "josue")
        add(7, "juges")
        add(8, "ruth")
        add(15, "esdras", "esd")
        add(16, "néhémie", "nehemie")
        add(19, "psaumes", "psaume")
        add(20, "proverbes")
        add(21, "ecclésiaste", "ecclesiaste", "qo")
        add(22, "cantique des cantiques", "cantique", "ct")
        add(23, "isaïe", "isaie")
        add(24, "jérémie", "jeremie")
        add(26, "ézéchiel", "ezechiel")
        add(28, "osée", "osee")
        add(29, "joël")
        add(31, "abdias")
        add(32, "jonas")
        add(33, "michée", "michee")
        add(34, "nahoum")
        add(35, "habaqouq", "habacuc")
        add(36, "sophonie")
        add(37, "aggée", "aggee")
        add(38, "zacharie")
        add(39, "malachie")
        add(40, "matthieu")
        add(41, "marc")
        add(42, "luc")
        add(43, "jean")
        add(44, "actes")
        add(45, "romains")
        add(46, "1 corinthiens", "1corinthiens")
        add(47, "2 corinthiens", "2corinthiens")
        add(48, "galates")
        add(49, "éphésiens", "ephesiens")
        add(50, "philippiens")
        add(51, "colossiens")
        add(52, "1 thessaloniciens", "1thessaloniciens")
        add(53, "2 thessaloniciens", "2thessaloniciens")
        add(54, "1 timothée", "1timothee")
        add(55, "2 timothée", "2timothee")
        add(56, "tite")
        add(57, "philémon", "philemon")
        add(58, "hébreux", "hebreux")
        add(59, "jacques")
        add(60, "1 pierre", "1pierre")
        add(61, "2 pierre", "2pierre")
        add(62, "1 jean", "1jean")
        add(63, "2 jean", "2jean")
        add(64, "3 jean", "3jean")
        add(66, "apocalypse")

        // ── Spanish (Español) ─────────────────────────────────────────────────
        add(1, "génesis")
        add(2, "éxodo", "exodo")
        add(3, "levítico", "levitico")
        add(4, "números", "numeros")
        add(5, "deuteronomio")
        add(6, "josué")
        add(7, "jueces", "jue")
        add(8, "rut")
        add(11, "1 reyes", "1reyes")
        add(12, "2 reyes", "2reyes")
        add(13, "1 crónicas", "1 cronicas", "1cronicas")
        add(14, "2 crónicas", "2 cronicas", "2cronicas")
        add(16, "nehemías", "nehemias")
        add(17, "ester")
        add(19, "salmos", "salmo", "sal")
        add(20, "proverbios")
        add(21, "eclesiastés", "eclesiastes", "ecl")
        add(22, "cantar de los cantares", "cantares", "cnt")
        add(23, "isaías", "isaias")
        add(24, "jeremías", "jeremias")
        add(25, "lamentaciones")
        add(26, "ezequiel")
        add(28, "oseas")
        add(30, "amós")
        add(31, "abdías", "abdias", "abd")
        add(32, "jonás")
        add(33, "miqueas")
        add(34, "nahúm")
        add(35, "habacuc")
        add(36, "sofonías", "sofonias", "sof")
        add(37, "hageo")
        add(38, "zacarías", "zacarias", "zac")
        add(39, "malaquías", "malaquias")
        add(40, "mateo")
        add(41, "marcos")
        add(42, "lucas")
        add(43, "juan")
        add(44, "hechos", "hch")
        add(45, "romanos")
        add(46, "1 corintios", "1corintios")
        add(47, "2 corintios", "2corintios")
        add(48, "gálatas", "galatas")
        add(49, "efesios", "ef")
        add(50, "filipenses", "fil")
        add(52, "1 tesalonicenses", "1tesalonicenses")
        add(53, "2 tesalonicenses", "2tesalonicenses")
        add(54, "1 timoteo", "1timoteo")
        add(55, "2 timoteo", "2timoteo")
        add(56, "tito")
        add(57, "filemón", "filemon")
        add(58, "hebreos")
        add(59, "santiago", "stg")
        add(60, "1 pedro", "1pedro")
        add(61, "2 pedro", "2pedro")
        add(62, "1 juan", "1juan")
        add(63, "2 juan", "2juan")
        add(64, "3 juan", "3juan")
        add(66, "apocalipsis", "ap")

        // ── Portuguese (Português) ────────────────────────────────────────────
        add(1, "gênesis", "gêneses")
        add(2, "êxodo")
        add(3, "levítico")
        add(4, "números")
        add(5, "deuteronômio", "deuteronomio")
        add(6, "josué")
        add(7, "juízes", "juizes")
        add(8, "rute")
        add(11, "1 reis", "1reis")
        add(12, "2 reis", "2reis")
        add(13, "1 crônicas", "1 cronicas", "1cronicas")
        add(14, "2 crônicas", "2 cronicas", "2cronicas")
        add(16, "neemias")
        add(18, "jó")
        add(19, "salmos", "salmo", "sl")
        add(20, "provérbios", "proverbios", "pv")
        add(21, "eclesiastes")
        add(22, "cântico dos cânticos", "cantares")
        add(23, "isaías")
        add(24, "jeremias")
        add(25, "lamentações", "lamentacoes")
        add(26, "ezequiel")
        add(28, "oseias")
        add(31, "obadias")
        add(32, "jonas")
        add(33, "miquéias", "miqueias")
        add(35, "habacuque")
        add(36, "sofonias", "sf")
        add(37, "ageu", "ag")
        add(38, "zacarias")
        add(39, "malaquias")
        add(40, "mateus")
        add(41, "marcos")
        add(42, "lucas")
        add(43, "joão", "joao")
        add(44, "atos", "at")
        add(45, "romanos")
        add(46, "1 coríntios", "1 corintios", "1corintios")
        add(47, "2 coríntios", "2 corintios", "2corintios")
        add(48, "gálatas")
        add(49, "efésios", "efesios")
        add(50, "filipenses")
        add(51, "colossenses")
        add(52, "1 tessalonicenses", "1tessalonicenses")
        add(53, "2 tessalonicenses", "2tessalonicenses")
        add(54, "1 timóteo", "1timoteo")
        add(55, "2 timóteo", "2timoteo")
        add(57, "filêmon")
        add(58, "hebreus")
        add(59, "tiago")
        add(60, "1 pedro", "1pedro")
        add(61, "2 pedro", "2pedro")
        add(62, "1 joão", "1joao", "1 joao")
        add(63, "2 joão", "2joao", "2 joao")
        add(64, "3 joão", "3joao", "3 joao")
        add(66, "apocalipse")

        // ── Ukrainian (Українська) ────────────────────────────────────────────
        add(1, "буття", "бут")
        add(2, "вихід", "вих")
        add(5, "повторення закону", "повторення", "повт")
        add(6, "ісус навин", "нав")
        add(7, "судді")
        add(8, "рут")
        add(9, "1 самуїла", "1 сам", "1сам")
        add(10, "2 самуїла", "2 сам", "2сам")
        add(11, "1 царів", "1царів")
        add(12, "2 царів", "2царів")
        add(13, "1 хроніки", "1хроніки", "1хр")
        add(14, "2 хроніки", "2хроніки", "2хр")
        add(16, "неємія", "неєм")
        add(17, "естер")
        add(18, "йов", "йв")
        add(19, "псалми")
        add(20, "приповісті", "прип")
        add(21, "екклезіяст", "еклезіяст", "еккл")
        add(22, "пісня пісень", "піс")
        add(23, "ісая", "іс")
        add(24, "єремія", "єр")
        add(25, "плач єремії")
        add(26, "єзекіїль", "єз")
        add(29, "йоїл", "йл")
        add(31, "овдій", "овд")
        add(32, "йона", "йон")
        add(35, "авакум", "авк")
        add(38, "захарія")
        add(39, "малахія")
        add(40, "матвія", "мт")
        add(43, "івана", "ін")
        add(44, "дії", "ді")
        add(52, "1 солунян", "1 фес", "1сол")
        add(53, "2 солунян", "2 фес", "2сол")
        add(58, "євреїв", "євр")
        add(59, "якова", "як")
        add(62, "1 івана", "1ін")
        add(63, "2 івана", "2ін")
        add(64, "3 івана", "3ін")
        add(65, "юди", "юд")
        add(66, "об'явлення", "одкровення", "об")

        // ── Romanian (Română) ─────────────────────────────────────────────────
        add(1, "geneza", "fc")
        add(2, "exodul", "ieșirea", "iesirea")
        add(3, "leviticul", "levitic")
        add(4, "numeri")
        add(5, "deuteronomul", "deuteronom")
        add(6, "iosua", "ios")
        add(7, "judecători", "judecatori")
        add(11, "1 regi", "1regi")
        add(12, "2 regi", "2regi")
        add(13, "1 cronici", "1cronici")
        add(14, "2 cronici", "2cronici")
        add(17, "estera")
        add(18, "iov")
        add(19, "psalmi")
        add(20, "proverbe")
        add(21, "eclesiastul", "ecclesiastul")
        add(22, "cântarea cântărilor", "cantarea cantarilor")
        add(23, "isaia")
        add(24, "ieremia")
        add(25, "plângerile", "plangerile")
        add(26, "ezechiel")
        add(28, "osea")
        add(29, "ioel")
        add(31, "obadia")
        add(32, "iona")
        add(33, "mica")
        add(35, "habacuc")
        add(36, "țefania", "tefania")
        add(37, "hagai")
        add(38, "zaharia")
        add(39, "maleahi")
        add(40, "matei")
        add(41, "marcu")
        add(42, "luca")
        add(43, "ioan", "in")
        add(44, "faptele apostolilor", "fapte", "fa")
        add(45, "romani")
        add(46, "1 corinteni", "1corinteni")
        add(47, "2 corinteni", "2corinteni")
        add(48, "galateni")
        add(49, "efeseni")
        add(50, "filipeni")
        add(51, "coloseni")
        add(52, "1 tesaloniceni", "1tesaloniceni")
        add(53, "2 tesaloniceni", "2tesaloniceni")
        add(54, "1 timotei", "1timotei")
        add(55, "2 timotei", "2timotei")
        add(56, "tit")
        add(57, "filimon")
        add(58, "evrei", "evr")
        add(59, "iacov", "iac")
        add(60, "1 petru", "1petru")
        add(61, "2 petru", "2petru")
        add(62, "1 ioan", "1in")
        add(63, "2 ioan", "2in")
        add(64, "3 ioan", "3in")
        add(65, "iuda", "iud")
        add(66, "apocalipsa")

        // ── Polish (Polski) ───────────────────────────────────────────────────
        add(1, "rodzaju", "rdz")
        add(2, "wyjścia", "wyjscia", "wj")
        add(3, "kapłańska", "kaplanska", "kpl")
        add(4, "liczb", "lb")
        add(5, "powtórzonego prawa", "powtorzonego prawa", "pwt")
        add(6, "jozuego", "joz")
        add(7, "sędziów", "sedziow", "sdz")
        add(9, "1 samuela", "1samuela", "1sm")
        add(10, "2 samuela", "2samuela", "2sm")
        add(11, "1 królów", "1 krolow", "1krl")
        add(12, "2 królów", "2 krolow", "2krl")
        add(13, "1 kronik", "1krn")
        add(14, "2 kronik", "2krn")
        add(15, "ezdrasza", "ezd")
        add(16, "nehemiasza")
        add(17, "estery")
        add(18, "hioba")
        add(19, "psalmów", "psalmow")
        add(20, "przysłów", "przyslow", "prz")
        add(21, "koheleta", "koh")
        add(22, "pieśni nad pieśniami", "piesni nad piesniami", "pnp")
        add(23, "izajasza", "iz")
        add(24, "jeremiasza")
        add(25, "lamentacje", "lm")
        add(26, "ezechiela")
        add(27, "daniela")
        add(28, "ozeasza", "oz")
        add(29, "joela")
        add(30, "amosa")
        add(31, "abdiasza", "ab")
        add(32, "jonasza")
        add(33, "micheasza")
        add(34, "nahuma")
        add(35, "habakuka", "ha")
        add(36, "sofoniasza", "so")
        add(37, "aggeusza", "ag")
        add(38, "zachariasza", "za")
        add(39, "malachiasza")
        add(40, "mateusza")
        add(41, "marka")
        add(42, "łukasza", "lukasza", "łk")
        add(43, "jana")
        add(44, "dziejów", "dzieje", "dz")
        add(45, "rzymian", "rz")
        add(46, "1 koryntian", "1koryntian")
        add(47, "2 koryntian", "2koryntian")
        add(48, "galatów", "galatow")
        add(49, "efezjan")
        add(50, "filipian")
        add(51, "kolosan")
        add(52, "1 tesaloniczan", "1tesaloniczan", "1tes")
        add(53, "2 tesaloniczan", "2tesaloniczan", "2tes")
        add(54, "1 tymoteusza", "1tymoteusza", "1tm")
        add(55, "2 tymoteusza", "2tymoteusza", "2tm")
        add(56, "tytusa")
        add(57, "filemona")
        add(58, "hebrajczyków", "hebrajczykow", "hbr")
        add(59, "jakuba", "jk", "jak")
        add(60, "1 piotra", "1piotra")
        add(61, "2 piotra", "2piotra")
        add(62, "1 jana", "1jana")
        add(63, "2 jana", "2jana")
        add(64, "3 jana", "3jana")
        add(65, "judy")
        add(66, "objawienia", "obj")
    }

    // Sorted longest-first for greedy matching; extended by register() at startup
    private var _aliasesByLength: List<Pair<String, Int>> =
        ALIASES.entries.sortedByDescending { it.key.length }.map { it.key to it.value }

    val ALIASES_BY_LENGTH: List<Pair<String, Int>> get() = _aliasesByLength

    // ── Inflection-tolerant single-token resolution ─────────────────────────────
    // Russian heavily inflects book names (Матфей→Матфея, Даниил→Даниила, Лука→Луки), so exact
    // alias lookup misses spoken forms. Build a stem index from single-word aliases and match by
    // prefix. Min stem length 4 keeps it from firing on 2–3 letter look-alikes.
    private const val MIN_STEM = 4
    private val RU_TRIM = "йьяиаеоуюёы".toSet()

    // internal (not private): ReferenceWatcher.AMBIGUOUS_BOOK_STEMS derives its keys from this so
    // they can never desync from the trimming rule actually used to build the stem index.
    internal fun stemOf(s: String): String {
        var x = s
        while (x.length > MIN_STEM && x.last() in RU_TRIM) x = x.dropLast(1)
        return x
    }

    private var _stemIndex: List<Pair<String, Int>> = buildStemIndex(ALIASES)

    private fun buildStemIndex(aliases: Map<String, Int>): List<Pair<String, Int>> =
        aliases.entries
            .filter { !it.key.contains(' ') && it.key.length >= MIN_STEM && it.key.any { c -> c in 'а'..'я' } }
            .map { stemOf(it.key) to it.value }
            .filter { it.first.length >= MIN_STEM }
            // If two book stems collide, drop both (ambiguous) to avoid a wrong guess.
            .groupBy { it.first }
            .filter { (_, v) -> v.map { it.second }.distinct().size == 1 }
            .map { (stem, v) -> stem to v.first().second }
            .sortedByDescending { it.first.length }

    /** A stem-prefix hit: which book and via which stem — the caller gates on how much longer
     *  the token is than the matched stem (see ReferenceWatcher.classify's over-extension gate). */
    data class StemMatch(val bookNum: Int, val stem: String)

    /** Resolves a single token to a book number by inflection-tolerant stem prefix, or null. */
    fun resolveStem(token: String): StemMatch? =
        _stemIndex.firstOrNull { token.length >= it.first.length && token.startsWith(it.first) }
            ?.let { StemMatch(it.second, it.first) }

    // ── Inflection-tolerant MULTI-word resolution ───────────────────────────────
    // A multi-word name inflects in both halves ("Плач Иеремии" → "Плача Иеремии"), and the STT
    // does not always agree with the alias table on which case each half is in. Exact lookup then
    // misses the phrase and the words classify independently — which is worse than not matching at
    // all when the second word is itself a book name: "книгу пророка Плача Иеремия" produced
    // Lamentations THEN Jeremiah, and ReferenceWatcher.interpret takes the last book before the
    // numbers, so a whole recorded sermon ran against Jeremiah 3 instead of Lamentations 3.
    // Keying by per-word stem collapses both halves' endings ("плача иеремия" and "плач иеремии"
    // both key to "плач иерем"), so any case combination the speaker or the STT produces resolves.
    private fun stemPhrase(words: List<String>): String = words.joinToString(" ") { stemOf(it) }

    private var _multiWordStems: Map<String, Int> = buildMultiWordStems(ALIASES)

    private fun buildMultiWordStems(aliases: Map<String, Int>): Map<String, Int> =
        aliases.entries
            .filter { it.key.contains(' ') && it.key.any { c -> c in 'а'..'я' } }
            .map { stemPhrase(it.key.split(' ')) to it.value }
            // Same collision rule as the single-token index: if two books share a stemmed phrase,
            // drop both rather than guess.
            .groupBy { it.first }
            .filter { (_, v) -> v.map { it.second }.distinct().size == 1 }
            .map { (stem, v) -> stem to v.first().second }
            .toMap()

    /**
     * Resolves a run of [words] to a book by matching every word's stem against a multi-word alias.
     * Returns null unless the whole run stems to exactly one known multi-word name — this never
     * matches a single token, so it cannot widen what a bare word resolves to.
     */
    fun resolveStemPhrase(words: List<String>): Int? =
        if (words.size < 2) null else _multiWordStems[stemPhrase(words)]

    // Stems that exist ONLY because an SPB module registered its own book name — i.e. never vetted
    // against real transcripts the way the static table above was. A module is free to name a book
    // with a word that is also ordinary vocabulary (the Russian Synodal names book 65 "Иуда", the
    // man Judas, and book 28 "Осия"), and such a name arrives here with no length or ambiguity
    // review at all. ReferenceWatcher.classify demands corroboration before letting a SHORT one of
    // these resolve; a static short alias is already gated in its own branch.
    private var _registeredOnlyStems: Set<String> = emptySet()

    internal fun isRegisteredOnlyStem(stem: String): Boolean = stem in _registeredOnlyStems

    // Called once at startup with (bookNum, bookName) pairs from all loaded SPB files.
    // Adds any name not already in the static alias table so every SPB language
    // gets explicit-reference support for free.
    fun register(spbBookNames: List<Pair<Int, String>>) {
        val combined = ALIASES.toMutableMap()
        for ((num, name) in spbBookNames) {
            val key = name.lowercase().replace('ё', 'е').trim() // fold ё→е like the tokenizer
            combined.putIfAbsent(key, num)
            // Same apostrophe-variant expansion as the static table (Ukrainian SPB book names).
            apostropheVariants(key).forEach { combined.putIfAbsent(it, num) }
        }
        _aliasesByLength = combined.entries.sortedByDescending { it.key.length }.map { it.key to it.value }
        _stemIndex = buildStemIndex(combined)
        _multiWordStems = buildMultiWordStems(combined)
        val staticStems = buildStemIndex(ALIASES).map { it.first }.toSet()
        _registeredOnlyStems = _stemIndex.map { it.first }.filterNot { it in staticStems }.toSet()
    }

    fun canonicalName(bookNum: Int): String = CANONICAL_NAMES[bookNum] ?: "Book $bookNum"

}

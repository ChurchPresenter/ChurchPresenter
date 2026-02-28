#!/usr/bin/env python3

import xml.etree.ElementTree as ET
import sys
import os
import glob

BOOK_NAMES = {
    1: "Genesis",
    2: "Exodus",
    3: "Leviticus",
    4: "Numbers",
    5: "Deuteronomy",
    6: "Joshua",
    7: "Judges",
    8: "Ruth",
    9: "1 Samuel",
    10: "2 Samuel",
    11: "1 Kings",
    12: "2 Kings",
    13: "1 Chronicles",
    14: "2 Chronicles",
    15: "Ezra",
    16: "Nehemiah",
    17: "Esther",
    18: "Job",
    19: "Psalms",
    20: "Proverbs",
    21: "Ecclesiastes",
    22: "Song of Solomon",
    23: "Isaiah",
    24: "Jeremiah",
    25: "Lamentations",
    26: "Ezekiel",
    27: "Daniel",
    28: "Hosea",
    29: "Joel",
    30: "Amos",
    31: "Obadiah",
    32: "Jonah",
    33: "Micah",
    34: "Nahum",
    35: "Habakkuk",
    36: "Zephaniah",
    37: "Haggai",
    38: "Zechariah",
    39: "Malachi",
    40: "Matthew",
    41: "Mark",
    42: "Luke",
    43: "John",
    44: "Acts",
    45: "Romans",
    46: "1 Corinthians",
    47: "2 Corinthians",
    48: "Galatians",
    49: "Ephesians",
    50: "Philippians",
    51: "Colossians",
    52: "1 Thessalonians",
    53: "2 Thessalonians",
    54: "1 Timothy",
    55: "2 Timothy",
    56: "Titus",
    57: "Philemon",
    58: "Hebrews",
    59: "James",
    60: "1 Peter",
    61: "2 Peter",
    62: "1 John",
    63: "2 John",
    64: "3 John",
    65: "Jude",
    66: "Revelation",
}

UKRAINIAN_BOOK_NAMES = {
    1: "Буття",
    2: "Вихід",
    3: "Левит",
    4: "Числа",
    5: "Повторний Закон",
    6: "Ісус Навин",
    7: "Книга Суддів",
    8: "Рут",
    9: "1-а Царств",
    10: "2-а Царств",
    11: "3-я Царств",
    12: "4-а Царств",
    13: "1-а Паралипоменон",
    14: "2-а Паралипоменон",
    15: "Ездра",
    16: "Неемія",
    17: "Естер",
    18: "Йов",
    19: "Псалтир",
    20: "Приповістей",
    21: "Екклесіаст",
    22: "Пісня Пісень",
    23: "Ісая",
    24: "Єремія",
    25: "Плач Єремії",
    26: "Єзекііль",
    27: "Даниїл",
    28: "Ося",
    29: "Йоель",
    30: "Амос",
    31: "Авдій",
    32: "Йона",
    33: "Михей",
    34: "Наум",
    35: "Аввакум",
    36: "Софонія",
    37: "Аггей",
    38: "Захарія",
    39: "Малахія",
    40: "Від Матвія",
    41: "Від Марка",
    42: "Від Луки",
    43: "Від Івана",
    44: "Діяння",
    45: "До Римлян",
    46: "1-е до Коринтян",
    47: "2-е до Коринтян",
    48: "До Галатів",
    49: "До Ефесян",
    50: "До Филип'ян",
    51: "До Колосян",
    52: "1-е до Солунян",
    53: "2-е до Солунян",
    54: "1-е до Тимофія",
    55: "2-е до Тимофія",
    56: "До Тита",
    57: "До Филимона",
    58: "До Євреїв",
    59: "Якова",
    60: "1-е Петра",
    61: "2-е Петра",
    62: "1-е Івана",
    63: "2-е Івана",
    64: "3-е Івана",
    65: "Юди",
    66: "Об'явлення",
}

RUSSIAN_BOOK_NAMES = {
    1: "Бытие",
    2: "Исход",
    3: "Левит",
    4: "Числа",
    5: "Второзаконие",
    6: "Иисус Навин",
    7: "Книга Судей",
    8: "Руфь",
    9: "1-я Царств",
    10: "2-я Царств",
    11: "3-я Царств",
    12: "4-я Царств",
    13: "1-я Паралипоменон",
    14: "2-я Паралипоменон",
    15: "Ездра",
    16: "Неемия",
    17: "Есфирь",
    18: "Иов",
    19: "Псалтирь",
    20: "Притчи",
    21: "Екклесиаст",
    22: "Песни Песней",
    23: "Исаия",
    24: "Иеремия",
    25: "Плач Иеремии",
    26: "Иезекииль",
    27: "Даниил",
    28: "Осия",
    29: "Иоиль",
    30: "Амос",
    31: "Авдий",
    32: "Иона",
    33: "Михей",
    34: "Наум",
    35: "Аввакум",
    36: "Софония",
    37: "Аггей",
    38: "Захария",
    39: "Малахия",
    40: "От Матфея",
    41: "От Марка",
    42: "От Луки",
    43: "От Иоанна",
    44: "Деяния",
    45: "К Римлянам",
    46: "1-е Коринфянам",
    47: "2-е Коринфянам",
    48: "К Галатам",
    49: "К Ефесянам",
    50: "К Филиппийцам",
    51: "К Колоссянам",
    52: "1-е Фессалоникийцам",
    53: "2-е Фессалоникийцам",
    54: "1-е Тимофею",
    55: "2-е Тимофею",
    56: "К Титу",
    57: "К Филимону",
    58: "К Евреям",
    59: "Иакова",
    60: "1-е Петра",
    61: "2-е Петра",
    62: "1-е Иоанна",
    63: "2-е Иоанна",
    64: "3-е Иоанна",
    65: "Иуды",
    66: "Откровение",
}

GERMAN_BOOK_NAMES = {
    1: "1. Mose",
    2: "2. Mose",
    3: "3. Mose",
    4: "4. Mose",
    5: "5. Mose",
    6: "Josua",
    7: "Richter",
    8: "Ruth",
    9: "1. Samuel",
    10: "2. Samuel",
    11: "1. Könige",
    12: "2. Könige",
    13: "1. Chronika",
    14: "2. Chronika",
    15: "Esra",
    16: "Nehemia",
    17: "Esther",
    18: "Hiob",
    19: "Psalm",
    20: "Sprüche",
    21: "Prediger",
    22: "Hohelied",
    23: "Jesaja",
    24: "Jeremia",
    25: "Klagelieder",
    26: "Hesekiel",
    27: "Daniel",
    28: "Hosea",
    29: "Joel",
    30: "Amos",
    31: "Obadja",
    32: "Jona",
    33: "Micha",
    34: "Nahum",
    35: "Habakuk",
    36: "Zephanja",
    37: "Haggai",
    38: "Sacharja",
    39: "Maleachi",
    40: "Matthäus",
    41: "Markus",
    42: "Lukas",
    43: "Johannes",
    44: "Apostelgeschichte",
    45: "Römer",
    46: "1. Korinther",
    47: "2. Korinther",
    48: "Galater",
    49: "Epheser",
    50: "Philipper",
    51: "Kolosser",
    52: "1. Thessalonicher",
    53: "2. Thessalonicher",
    54: "1. Timotheus",
    55: "2. Timotheus",
    56: "Titus",
    57: "Philemon",
    58: "Hebräer",
    59: "Jakobus",
    60: "1. Petrus",
    61: "2. Petrus",
    62: "1. Johannes",
    63: "2. Johannes",
    64: "3. Johannes",
    65: "Judas",
    66: "Offenbarung",
}

FRENCH_BOOK_NAMES = {
    1: "Genèse",
    2: "Exode",
    3: "Lévitique",
    4: "Nombres",
    5: "Deutéronome",
    6: "Josué",
    7: "Juges",
    8: "Ruth",
    9: "1 Samuel",
    10: "2 Samuel",
    11: "1 Rois",
    12: "2 Rois",
    13: "1 Chroniques",
    14: "2 Chroniques",
    15: "Esdras",
    16: "Néhémie",
    17: "Esther",
    18: "Job",
    19: "Psaumes",
    20: "Proverbes",
    21: "Ecclésiaste",
    22: "Cantique des cantiques",
    23: "Ésaïe",
    24: "Jérémie",
    25: "Lamentations",
    26: "Ézéchiel",
    27: "Daniel",
    28: "Osée",
    29: "Joël",
    30: "Amos",
    31: "Abdias",
    32: "Jonas",
    33: "Michée",
    34: "Nahum",
    35: "Habacuc",
    36: "Sophonie",
    37: "Aggée",
    38: "Zacharie",
    39: "Malachie",
    40: "Matthieu",
    41: "Marc",
    42: "Luc",
    43: "Jean",
    44: "Actes",
    45: "Romains",
    46: "1 Corinthiens",
    47: "2 Corinthiens",
    48: "Galates",
    49: "Éphésiens",
    50: "Philippiens",
    51: "Colossiens",
    52: "1 Thessaloniciens",
    53: "2 Thessaloniciens",
    54: "1 Timothée",
    55: "2 Timothée",
    56: "Tite",
    57: "Philémon",
    58: "Hébreux",
    59: "Jacques",
    60: "1 Pierre",
    61: "2 Pierre",
    62: "1 Jean",
    63: "2 Jean",
    64: "3 Jean",
    65: "Jude",
    66: "Apocalypse",
}

SPANISH_BOOK_NAMES = {
    1: "Génesis",
    2: "Éxodo",
    3: "Levítico",
    4: "Números",
    5: "Deuteronomio",
    6: "Josué",
    7: "Jueces",
    8: "Ruth",
    9: "1 Samuel",
    10: "2 Samuel",
    11: "1 Reyes",
    12: "2 Reyes",
    13: "1 Crónicas",
    14: "2 Crónicas",
    15: "Esdras",
    16: "Nehemías",
    17: "Ester",
    18: "Job",
    19: "Salmos",
    20: "Proverbios",
    21: "Eclesiastés",
    22: "Cantar de los cantares",
    23: "Isaías",
    24: "Jeremías",
    25: "Lamentaciones",
    26: "Ezequiel",
    27: "Daniel",
    28: "Oseas",
    29: "Joel",
    30: "Amós",
    31: "Abdías",
    32: "Jonás",
    33: "Miqueas",
    34: "Nahum",
    35: "Habacuc",
    36: "Sofonías",
    37: "Hageo",
    38: "Zacarías",
    39: "Malaquías",
    40: "Mateo",
    41: "Marcos",
    42: "Lucas",
    43: "Juan",
    44: "Hechos",
    45: "Romanos",
    46: "1 Corintios",
    47: "2 Corintios",
    48: "Gálatas",
    49: "Efesios",
    50: "Filipenses",
    51: "Colosenses",
    52: "1 Tesalonicenses",
    53: "2 Tesalonicenses",
    54: "1 Timoteo",
    55: "2 Timoteo",
    56: "Tito",
    57: "Filemón",
    58: "Hebreos",
    59: "Santiago",
    60: "1 Pedro",
    61: "2 Pedro",
    62: "1 Juan",
    63: "2 Juan",
    64: "3 Juan",
    65: "Judas",
    66: "Apocalipsis",
}

PORTUGUESE_BOOK_NAMES = {
    1: "Gênesis",
    2: "Êxodo",
    3: "Levítico",
    4: "Números",
    5: "Deuteronômio",
    6: "Josué",
    7: "Juízes",
    8: "Rute",
    9: "1 Samuel",
    10: "2 Samuel",
    11: "1 Reis",
    12: "2 Reis",
    13: "1 Crônicas",
    14: "2 Crônicas",
    15: "Esdras",
    16: "Neemias",
    17: "Ester",
    18: "Jó",
    19: "Salmos",
    20: "Provérbios",
    21: "Eclesiastes",
    22: "Cântico dos Cânticos",
    23: "Isaías",
    24: "Jeremias",
    25: "Lamentações",
    26: "Ezequiel",
    27: "Daniel",
    28: "Oséias",
    29: "Joel",
    30: "Amós",
    31: "Obadias",
    32: "Jonás",
    33: "Miquéias",
    34: "Naum",
    35: "Habacuque",
    36: "Sofonias",
    37: "Ageu",
    38: "Zacarias",
    39: "Malaquias",
    40: "Mateus",
    41: "Marcos",
    42: "Lucas",
    43: "João",
    44: "Atos",
    45: "Romanos",
    46: "1 Coríntios",
    47: "2 Coríntios",
    48: "Gálatas",
    49: "Efésios",
    50: "Filipenses",
    51: "Colossenses",
    52: "1 Tessalonicenses",
    53: "2 Tessalonicenses",
    54: "1 Timóteo",
    55: "2 Timóteo",
    56: "Tito",
    57: "Filemón",
    58: "Hebreus",
    59: "Tiago",
    60: "1 Pedro",
    61: "2 Pedro",
    62: "1 João",
    63: "2 João",
    64: "3 João",
    65: "Judas",
    66: "Apocalipse",
}

ITALIAN_BOOK_NAMES = {
    1: "Genesi",
    2: "Esodo",
    3: "Levitico",
    4: "Numeri",
    5: "Deuteronomio",
    6: "Giosuè",
    7: "Giudici",
    8: "Rut",
    9: "1 Samuele",
    10: "2 Samuele",
    11: "1 Re",
    12: "2 Re",
    13: "1 Cronache",
    14: "2 Cronache",
    15: "Esdra",
    16: "Neemia",
    17: "Ester",
    18: "Giobbe",
    19: "Salmi",
    20: "Proverbi",
    21: "Ecclesiaste",
    22: "Cantico dei cantici",
    23: "Isaia",
    24: "Geremia",
    25: "Lamentazioni",
    26: "Ezechiele",
    27: "Daniele",
    28: "Osea",
    29: "Gioele",
    30: "Amos",
    31: "Abdìa",
    32: "Giona",
    33: "Michea",
    34: "Naum",
    35: "Abacuc",
    36: "Sofonia",
    37: "Aggeo",
    38: "Zaccaria",
    39: "Malachia",
    40: "Matteo",
    41: "Marco",
    42: "Luca",
    43: "Giovanni",
    44: "Atti",
    45: "Romani",
    46: "1 Corinzi",
    47: "2 Corinzi",
    48: "Galati",
    49: "Efesini",
    50: "Filippesi",
    51: "Colossesi",
    52: "1 Tessalonicenses",
    53: "2 Tessalonicenses",
    54: "1 Timoteo",
    55: "2 Timoteo",
    56: "Tito",
    57: "Filemone",
    58: "Ebrei",
    59: "Giacomo",
    60: "1 Pietro",
    61: "2 Pietro",
    62: "1 Giovanni",
    63: "2 Giovanni",
    64: "3 Giovanni",
    65: "Giuda",
    66: "Apocalisse",
}

DANISH_BOOK_NAMES = {
    1: "1 Mosebog",
    2: "2 Mosebog",
    3: "3 Mosebog",
    4: "4 Mosebog",
    5: "5 Mosebog",
    6: "Josua",
    7: "Dommerbogen",
    8: "Rut",
    9: "1 Samuelisbog",
    10: "2 Samuelisbog",
    11: "1 Kongebog",
    12: "2 Kongebog",
    13: "1 Krønikebog",
    14: "2 Krønikebog",
    15: "Esra",
    16: "Nehemias",
    17: "Ester",
    18: "Job",
    19: "Salmerne",
    20: "Ordsprogene",
    21: "Prædikerens Bog",
    22: "Højsangen",
    23: "Esajas",
    24: "Jeremias",
    25: "Klagesangene",
    26: "Ezekiels Bog",
    27: " Daniels Bog",
    28: "Hoseas",
    29: "Joel",
    30: "Amos",
    31: "Obadjas",
    32: "Jonas",
    33: "Mika",
    34: "Nahum",
    35: "Habakuk",
    36: "Sefanias",
    37: "Haggaj",
    38: "Zakarias",
    39: "Malaki",
    40: "Matthæus",
    41: "Markus",
    42: "Lukas",
    43: "Johannes",
    44: "Apostlenes Gerninger",
    45: "Romerbrevet",
    46: "1 Korintherbrev",
    47: "2 Korintherbrev",
    48: "Galaterbrevet",
    49: "Efeserbrevet",
    50: "Filipperbrevet",
    51: "Kolosserbrevet",
    52: "1 Thessalonikerbrev",
    53: "2 Thessalonikerbrev",
    54: "1 Timotheusbrev",
    55: "2 Timotheusbrev",
    56: "Titusbrevet",
    57: "Filemonbrevet",
    58: "Hebræerbrevet",
    59: "Jakobs Brev",
    60: "1 Peters Brev",
    61: "2 Peters Brev",
    62: "1 Johannes Brev",
    63: "2 Johannes Brev",
    64: "3 Johannes Brev",
    65: " Judas's Brev",
    66: "Aabenbaring",
}

SWEDISH_BOOK_NAMES = {
    1: "1 Mosebok",
    2: "2 Mosebok",
    3: "3 Mosebok",
    4: "4 Mosebok",
    5: "5 Mosebok",
    6: "Josua",
    7: "Domarboken",
    8: "Rut",
    9: "1 Samuelsboken",
    10: "2 Samuelsboken",
    11: "1 Kungaboken",
    12: "2 Kungaboken",
    13: "1 Krönikeboken",
    14: "2 Krönikeboken",
    15: "Esra",
    16: "Nehemia",
    17: "Ester",
    18: "Job",
    19: "Psaltaren",
    20: "Ordspråken",
    21: "Predikaren",
    22: "Höga visan",
    23: "Jesaja",
    24: "Jeremia",
    25: "Klagovisorna",
    26: "Hesekiel",
    27: "Daniel",
    28: "Hosea",
    29: "Joel",
    30: "Amos",
    31: "Obadja",
    32: "Jona",
    33: "Mika",
    34: "Nahum",
    35: "Habakuk",
    36: "Sefanja",
    37: "Haggai",
    38: "Sakarja",
    39: "Malaki",
    40: "Matteus",
    41: "Markus",
    42: "Lukas",
    43: "Johannes",
    44: "Apostlagärningarna",
    45: "Romarbrevet",
    46: "1 Korintierbrevet",
    47: "2 Korintierbrevet",
    48: "Galaterbrevet",
    49: "Efesierbrevet",
    50: "Filipperbrevet",
    51: "Kolosserbrevet",
    52: "1 Tessalonikerbrevet",
    53: "2 Tessalonikerbrevet",
    54: "1 Timotheusbrevet",
    55: "2 Timotheusbrevet",
    56: "Titusbrevet",
    57: "Filemonbrevet",
    58: "Hebreerbrevet",
    59: "Jakobs brev",
    60: "1 Petrusbrev",
    61: "2 Petrusbrev",
    62: "1 Johannesbrev",
    63: "2 Johannesbrev",
    64: "3 Johannesbrev",
    65: "Judas brev",
    66: "Uppenbarelseboken",
}

NORWEGIAN_BOOK_NAMES = {
    1: "1 Mosebok",
    2: "2 Mosebok",
    3: "3 Mosebok",
    4: "4 Mosebok",
    5: "5 Mosebok",
    6: "Josva",
    7: "Dommerboka",
    8: "Rut",
    9: "1 Samuelsboka",
    10: "2 Samuelsboka",
    11: "1 Kongeboka",
    12: "2 Kongeboka",
    13: "1 Krønikebok",
    14: "2 Krønikebok",
    15: "Esra",
    16: "Nehemia",
    17: "Ester",
    18: "Job",
    19: "Salme",
    20: "Ordspråka",
    21: "Predikaren",
    22: "Høysangen",
    23: "Jesaja",
    24: "Jeremia",
    25: "Klagesangene",
    26: "Esekiel",
    27: "Daniel",
    28: "Hosea",
    29: "Joel",
    30: "Amos",
    31: "Obadja",
    32: "Jona",
    33: "Mika",
    34: "Nahum",
    35: "Habakkuk",
    36: "Sefanias",
    37: "Haggai",
    38: "Sakarja",
    39: "Malaki",
    40: "Matteus",
    41: "Markus",
    42: "Lukas",
    43: "Johannes",
    44: "Apostlenes gjerninger",
    45: "Romerbrevet",
    46: "1 Korinterbrev",
    47: "2 Korinterbrev",
    48: "Galaterbrevet",
    49: "Efesierbrevet",
    50: "Filipperbrevet",
    51: "Kolosserbrevet",
    52: "1 Tessalonikerbrev",
    53: "2 Tessalonikerbrev",
    54: "1 Timoteusbrev",
    55: "2 Timoteusbrev",
    56: "Titusbrevet",
    57: "Filemonbrevet",
    58: "Hebreerbrevet",
    59: "Jakobs brev",
    60: "1 Peters brev",
    61: "2 Peters brev",
    62: "1 Johannes brev",
    63: "2 Johannes brev",
    64: "3 Johannes brev",
    65: "Judas brev",
    66: "Åpenbaringen",
}

DUTCH_BOOK_NAMES = {
    1: "Genesis",
    2: "Exodus",
    3: "Leviticus",
    4: "Numeri",
    5: "Deuteronomium",
    6: "Jozua",
    7: "Richteren",
    8: "Ruth",
    9: "1 Samuel",
    10: "2 Samuel",
    11: "1 Koningen",
    12: "2 Koningen",
    13: "1 Kronieken",
    14: "2 Kronieken",
    15: "Ezra",
    16: "Nehemia",
    17: "Ester",
    18: "Job",
    19: "Psalmen",
    20: "Spreuken",
    21: "Prediker",
    22: "Hooglied",
    23: "Jesaja",
    24: "Jeremia",
    25: "Klaagliederen",
    26: "Ezechiel",
    27: "Daniel",
    28: "Hosea",
    29: "Joël",
    30: "Amos",
    31: "Obadja",
    32: "Jona",
    33: "Micha",
    34: "Nahum",
    35: "Habakuk",
    36: "Zefanja",
    37: "Haggai",
    38: "Zacharia",
    39: "Maleachi",
    40: "Matteüs",
    41: "Marcus",
    42: "Lucas",
    43: "Johannes",
    44: "Handelingen",
    45: "Romeinen",
    46: "1 Korintiërs",
    47: "2 Korintiërs",
    48: "Galaten",
    49: "Efeziërs",
    50: "Filippenzen",
    51: "Kolossenzen",
    52: "1 Tessalonicenzen",
    53: "2 Tessalonicenzen",
    54: "1 Timoteus",
    55: "2 Timoteus",
    56: "Titus",
    57: "Filemon",
    58: "Hebreeën",
    59: "Jakobus",
    60: "1 Petrus",
    61: "2 Petrus",
    62: "1 Johannes",
    63: "2 Johannes",
    64: "3 Johannes",
    65: "Judas",
    66: "Openbaring",
}

FINNISH_BOOK_NAMES = {
    1: "1. Mooseksen kirja",
    2: "2. Mooseksen kirja",
    3: "3. Mooseksen kirja",
    4: "4. Mooseksen kirja",
    5: "5. Mooseksen kirja",
    6: "Joosua",
    7: "Tuomarit",
    8: "Ruut",
    9: "1. Samuelin kirja",
    10: "2. Samuelin kirja",
    11: "1. Kuninkaiden kirja",
    12: "2. Kuninkaiden kirja",
    13: "1. Aikakirja",
    14: "2. Aikakirja",
    15: "Esra",
    16: "Nehemia",
    17: "Ester",
    18: "Job",
    19: "Psalmit",
    20: "Sananlaskut",
    21: "Saarnaaja",
    22: "Lauluja",
    23: "Jesaja",
    24: "Jeremia",
    25: "Valitukset",
    26: "Hesekiel",
    27: "Daniel",
    28: "Hoosea",
    29: "Joel",
    30: "Aamoksen",
    31: "Obadja",
    32: "Jona",
    33: "Mika",
    34: "Nahum",
    35: "Habakuk",
    36: "Sefanja",
    37: "Haggai",
    38: "Sakarja",
    39: "Malakia",
    40: "Matteus",
    41: "Markus",
    42: "Luukas",
    43: "Johannes",
    44: "Apostolien teot",
    45: "Roomalaisille",
    46: "1. Korinttolaisille",
    47: "2. Korinttolaisille",
    48: "Galatalaisille",
    49: "Efesolaisille",
    50: "Filippiläisille",
    51: "Kolossalaisille",
    52: "1. Tessalonikalaisille",
    53: "2. Tessalonikalaisille",
    54: "1. Timoteukselle",
    55: "2. Timoteukselle",
    56: "Titukselle",
    57: "Filemonille",
    58: "Hebrealaisille",
    59: "Jaakob",
    60: "1. Pietari",
    61: "2. Pietari",
    62: "1. Johannes",
    63: "2. Johannes",
    64: "3. Johannes",
    65: "Juudas",
    66: "Ilmestys",
}

POLISH_BOOK_NAMES = {
    1: "Księga Rodzaju",
    2: "Księga Wyjścia",
    3: "Księga Kapłańska",
    4: "Księga Liczb",
    5: "Księga Powtórzonego Prawa",
    6: "Księga Jozuego",
    7: "Księga Sędziów",
    8: "Księga Rut",
    9: "1 Księga Samuela",
    10: "2 Księga Samuela",
    11: "1 Księga Królewska",
    12: "2 Księga Królewska",
    13: "1 Księga Kronik",
    14: "2 Księga Kronik",
    15: "Księga Ezdrasza",
    16: "Księga Nehemiasza",
    17: "Księga Estery",
    18: "Księga Hioba",
    19: "Księga Psalmów",
    20: "Księga Przysłów",
    21: "Księga Koheleta",
    22: "Pieśń nad Pieśniami",
    23: "Księga Izajasza",
    24: "Księga Jeremiasza",
    25: "Treny",
    26: "Księga Ezechiela",
    27: "Księga Daniela",
    28: "Księga Ozeasza",
    29: "Księga Joela",
    30: "Księga Amosa",
    31: "Księga Abdiasza",
    32: "Księga Jonasza",
    33: "Księga Micheasza",
    34: "Księga Nahuma",
    35: "Księga Habakuka",
    36: "Księga Sofoniasza",
    37: "Księga Aggeusza",
    38: "Księga Zachariasza",
    39: "Księga Malachiasza",
    40: "Ewangelia według świętego Mateusza",
    41: "Ewangelia według świętego Marka",
    42: "Ewangelia według świętego Łukasza",
    43: "Ewangelia według świętego Jana",
    44: "Dzieje Apostolskie",
    45: "List do Rzymian",
    46: "1 List do Koryntian",
    47: "2 List do Koryntian",
    48: "List do Galatów",
    49: "List do Efezjan",
    50: "List do Filipian",
    51: "List do Kolosan",
    52: "1 List do Tesaloniczan",
    53: "2 List do Tesaloniczan",
    54: "1 List do Tymoteusza",
    55: "2 List do Tymoteusza",
    56: "List do Tytusa",
    57: "List do Filemona",
    58: "List do Hebrajczyków",
    59: "List świętego Jakuba",
    60: "1 List świętego Piotra",
    61: "2 List świętego Piotra",
    62: "1 List świętego Jana",
    63: "2 List świętego Jana",
    64: "3 List świętego Jana",
    65: "List Judy",
    66: "Księga Apokalipsy",
}

HUNGARIAN_BOOK_NAMES = {
    1: "Mózes első könyve",
    2: "Mózes második könyve",
    3: "Mózes harmadik könyve",
    4: "Mózes negyedik könyve",
    5: "Mózes ötödik könyve",
    6: "Józsué könyve",
    7: "Bírák könyve",
    8: "Ruth könyve",
    9: "Sámuel első könyve",
    10: "Sámuel második könyve",
    11: "Királyok első könyve",
    12: "Királyok második könyve",
    13: "Krónikák első könyve",
    14: "Krónikák második könyve",
    15: "Ezdrás könyve",
    16: "Nehémiás könyve",
    17: "Eszter könyve",
    18: "Jób könyve",
    19: "Zsoltárok könyve",
    20: "Példabeszédek könyve",
    21: "Prédikátor könyve",
    22: "Énekek éneke",
    23: "Ézsaiás prófétája",
    24: "Jeremiás prófétája",
    25: "Jeremiás siralmai",
    26: "Ezékiel prófétája",
    27: "Dániel prófétája",
    28: "Hóseás prófétája",
    29: "Jóel prófétája",
    30: "Ámos prófétája",
    31: "Abdiás prófétája",
    32: "Jónás prófétája",
    33: "Mikeás prófétája",
    34: "Náhum prófétája",
    35: "Habakuk prófétája",
    36: "Szofóniás prófétája",
    37: "Aggeus prófétája",
    38: "Zakariás prófétája",
    39: "Malakiás prófétája",
    40: "Máté evangéliuma",
    41: "Márk evangéliuma",
    42: "Lukács evengeliuma",
    43: "János evengeliuma",
    44: "Az apostolok cselekedetei",
    45: "A rómaiakhoz írt levél",
    46: "A korintusiakhoz írt első levél",
    47: "A korintusiakhoz írt második levél",
    48: "A galatákhoz írt levél",
    49: "Az efezusiakhoz írt levél",
    50: "A filippiekhez írt levél",
    51: "A kolosséiakhoz írt levél",
    52: "A tesszalonikiekhez írt első levél",
    53: "A tesszalonikiekhez írt második levél",
    54: "A timóteushoz írt első levél",
    55: "A timóteushoz írt második levél",
    56: "Títushoz írt levél",
    57: "Filemonhoz írt levél",
    58: "A zsidókhoz írt levél",
    59: "Jakab levele",
    60: "Péter első levele",
    61: "Péter második levele",
    62: "János első levele",
    63: "János második levele",
    64: "János harmadik levele",
    65: "Júdás levele",
    66: "Jelenések könyve",
}

CZECH_BOOK_NAMES = {
    1: "Genesis",
    2: "Exodus",
    3: "Leviticus",
    4: "Numeri",
    5: "Deuteronomium",
    6: "Jozue",
    7: "Soudců",
    8: "Rút",
    9: "1 Samuelova",
    10: "2 Samuelova",
    11: "1 Královská",
    12: "2 Královská",
    13: "1 Paralipomenon",
    14: "2 Paralipomenon",
    15: "Ezdra",
    16: "Nehemiáš",
    17: "Estera",
    18: "Jób",
    19: "Žalmy",
    20: "Přísloví",
    21: "Kazatel",
    22: "Píseň písní",
    23: "Izaiáš",
    24: "Jeremiáš",
    25: "Pláč",
    26: "Ezechiel",
    27: "Daniel",
    28: "Ozeáš",
    29: "Joel",
    30: "Amos",
    31: "Abdijáš",
    32: "Jonáš",
    33: "Micheáš",
    34: "Nahum",
    35: "Abakuk",
    36: "Sofoniáš",
    37: "Ageus",
    38: "Zachariáš",
    39: "Maleachi",
    40: "Matouš",
    41: "Marek",
    42: "Lukáš",
    43: "Jan",
    44: "Skutky apoštolů",
    45: "Římanům",
    46: "1 Korintským",
    47: "2 Korintským",
    48: "Galatským",
    49: "Efeským",
    50: "Filipským",
    51: "Koloským",
    52: "1 Tesalonickým",
    53: "2 Tesalonickým",
    54: "1 Timoteovi",
    55: "2 Timoteovi",
    56: "Titovi",
    57: "Filemonovi",
    58: "Židům",
    59: "Jakubův",
    60: "1 Petrův",
    61: "2 Petrův",
    62: "1 Janův",
    63: "2 Janův",
    64: "3 Janův",
    65: "Judův",
    66: "Zjevení",
}

ROMANIAN_BOOK_NAMES = {
    1: "Geneza",
    2: "Exodul",
    3: "Leviticul",
    4: "Numerii",
    5: "Deuteronomul",
    6: "Iosua",
    7: "Judecători",
    8: "Rut",
    9: "1 Samuel",
    10: "2 Samuel",
    11: "1 Împăraţi",
    12: "2 Împăraţi",
    13: "1 Cronici",
    14: "2 Cronici",
    15: "Ezra",
    16: "Neemia",
    17: "Estera",
    18: "Iov",
    19: "Psalmii",
    20: "Proverbele",
    21: "Ecleziastul",
    22: "Cântarea Cântărilor",
    23: "Isaia",
    24: "Ieremia",
    25: "Plângerile lui Ieremia",
    26: "Ezechiel",
    27: "Daniel",
    28: "Osea",
    29: "Ioel",
    30: "Amos",
    31: "Obadia",
    32: "Iona",
    33: "Micheea",
    34: "Naum",
    35: "Habacuc",
    36: "Sofonia",
    37: "Hagai",
    38: "Zaharia",
    39: "Maleahi",
    40: "Matei",
    41: "Marcu",
    42: "Luca",
    43: "Ioan",
    44: "Faptele Apostolilor",
    45: "Romani",
    46: "1 Corinteni",
    47: "2 Corinteni",
    48: "Galateni",
    49: "Efeseni",
    50: "Filipeni",
    51: "Coloseni",
    52: "1 Tesaloniceni",
    53: "2 Tesaloniceni",
    54: "1 Timotei",
    55: "2 Timotei",
    56: "Tit",
    57: "Filimon",
    58: "Evrei",
    59: "Iacov",
    60: "1 Petru",
    61: "2 Petru",
    62: "1 Ioan",
    63: "2 Ioan",
    64: "3 Ioan",
    65: "Iuda",
    66: "Apocalipsa",
}

GREEK_BOOK_NAMES = {
    1: "Γένεση",
    2: "Έξοδος",
    3: "Λευιτικός",
    4: "Αριθμοί",
    5: "Δευτερονόμιο",
    6: "Ιησούς του Ναυή",
    7: "Κριτές",
    8: "Ρουθ",
    9: "1 Σαμουήλ",
    10: "2 Σαμουήλ",
    11: "1 Βασιλειών",
    12: "2 Βασιλειών",
    13: "1 Παραλειπομένων",
    14: "2 Παραλειπομένων",
    15: "Έσδρας",
    16: "Νεεμίας",
    17: "Εσθήρ",
    18: "Ιώβ",
    19: "Ψαλμοί",
    20: "Παροιμίες",
    21: "Εκκλησιαστής",
    22: "Άσμα Ασμάτων",
    23: "Ησαΐας",
    24: "Ιερεμίας",
    25: "Θρήνοι",
    26: "Ιεζεκιήλ",
    27: "Δανιήλ",
    28: "Ωσηέ",
    29: "Ιωήλ",
    30: "Αμώς",
    31: "Αβδιού",
    32: "Ιωνάς",
    33: "Μιχαίας",
    34: "Ναούμ",
    35: "Αβακούμ",
    36: "Σοφονίας",
    37: "Αγγαίος",
    38: "Ζαχαρίας",
    39: "Μαλαχίας",
    40: "Ματθαίος",
    41: "Μάρκος",
    42: "Λουκάς",
    43: "Ιωάννης",
    44: "Πράξεις Αποστόλων",
    45: "Ρωμαίους",
    46: "1 Κορινθίους",
    47: "2 Κορινθίους",
    48: "Γαλάτας",
    49: "Εφεσίους",
    50: "Φιλιππησίους",
    51: "Κολοσσαείς",
    52: "1 Θεσσαλονικείς",
    53: "2 Θεσσαλονικείς",
    54: "1 Τιμόθεο",
    55: "2 Τιμόθεο",
    56: "Τίτον",
    57: "Φιλήμονα",
    58: "Εβραίους",
    59: "Ιακώβου",
    60: "1 Πέτρου",
    61: "2 Πέτρου",
    62: "1 Ιωάννου",
    63: "2 Ιωάννου",
    64: "3 Ιωάννου",
    65: "Ιούδα",
    66: "Αποκάλυψη",
}

TURKISH_BOOK_NAMES = {
    1: "Yaratılış",
    2: "Çıkış",
    3: "Levililer",
    4: "Sayılar",
    5: "Yasanın Tekrarı",
    6: "Yeşu",
    7: "Hakimler",
    8: "Rut",
    9: "1. Samuel",
    10: "2. Samuel",
    11: "1. Krallar",
    12: "2. Krallar",
    13: "1. Tarihler",
    14: "2. Tarihler",
    15: "Ezra",
    16: "Nehemya",
    17: "Ester",
    18: "Eyüp",
    19: "Zebur",
    20: "Meseller",
    21: "Vaiz",
    22: "Ezgiler Ezgisi",
    23: "Yeşaya",
    24: "Yeremya",
    25: "Ağıtlar",
    26: "Hezekiel",
    27: "Daniel",
    28: "Hoşea",
    29: "Yoel",
    30: "Amos",
    31: "Obadya",
    32: "Yunus",
    33: "Mika",
    34: "Nahum",
    35: "Habakkuk",
    36: "Sefanya",
    37: "Haggay",
    38: "Zekeriya",
    39: "Malaki",
    40: "Matta",
    41: "Markos",
    42: "Luka",
    43: "Yuhanna",
    44: "Elçilerin İşleri",
    45: "Romalılar",
    46: "1. Korintliler",
    47: "2. Korintliler",
    48: "Galatyalılar",
    49: "Efesliler",
    50: "Filipililer",
    51: "Koloseliler",
    52: "1. Selanikliler",
    53: "2. Selanikliler",
    54: "1. Timoteos",
    55: "2. Timoteos",
    56: "Titus",
    57: "Filimon",
    58: "İbraniler",
    59: "Yakup",
    60: "1. Petrus",
    61: "2. Petrus",
    62: "1. Yuhanna",
    63: "2. Yuhanna",
    64: "3. Yuhanna",
    65: "Yahuda",
    66: "Vahiy",
}

LANGUAGE_LOOKUPS = {
    "UKR": UKRAINIAN_BOOK_NAMES,
    "RUS": RUSSIAN_BOOK_NAMES,
    "GER": GERMAN_BOOK_NAMES,
    "DEU": GERMAN_BOOK_NAMES,
    "FRE": FRENCH_BOOK_NAMES,
    "SPA": SPANISH_BOOK_NAMES,
    "ESP": SPANISH_BOOK_NAMES,
    "POR": PORTUGUESE_BOOK_NAMES,
    "ITA": ITALIAN_BOOK_NAMES,
    "DAN": DANISH_BOOK_NAMES,
    "SWE": SWEDISH_BOOK_NAMES,
    "NOR": NORWEGIAN_BOOK_NAMES,
    "NL": DUTCH_BOOK_NAMES,
    "FIN": FINNISH_BOOK_NAMES,
    "POL": POLISH_BOOK_NAMES,
    "HUN": HUNGARIAN_BOOK_NAMES,
    "CZE": CZECH_BOOK_NAMES,
    "CES": CZECH_BOOK_NAMES,
    "ROM": ROMANIAN_BOOK_NAMES,
    "RUM": ROMANIAN_BOOK_NAMES,
    "GRE": GREEK_BOOK_NAMES,
    "GRC": GREEK_BOOK_NAMES,
    "TUR": TURKISH_BOOK_NAMES,
}


def get_book_name(book_elem, book_num, language=None):
    bname = book_elem.get("bname", "")
    if bname:
        return bname

    bsname = book_elem.get("bsname", "")
    if bsname:
        return bsname

    first_chap = book_elem.find("CHAPTER")
    if first_chap is not None:
        caption = first_chap.find("CAPTION")
        if caption is not None and caption.text:
            caption_text = caption.text.strip()
            if "." in caption_text:
                parts = caption_text.rsplit(".", 1)
                short_name = parts[-1].strip()
                if short_name and len(short_name) < 30:
                    return short_name

    if language and language in LANGUAGE_LOOKUPS:
        return LANGUAGE_LOOKUPS[language].get(
            book_num, BOOK_NAMES.get(book_num, f"Book {book_num}")
        )

    return BOOK_NAMES.get(book_num, f"Book {book_num}")


def parse_xml_bible(xml_path):
    tree = ET.parse(xml_path)
    root = tree.getroot()

    bible_name = root.get("biblename", "Unknown")

    info = root.find("INFORMATION")
    description = ""
    language = None
    if info is not None:
        desc_elem = info.find("description")
        if desc_elem is not None:
            description = desc_elem.text or ""
        lang_elem = info.find("language")
        if lang_elem is not None and lang_elem.text:
            xml_lang = lang_elem.text.strip().upper()
            path_lower = xml_path.lower()
            if xml_lang == "RUS" and (
                "ukrainian" in path_lower or "українська" in path_lower
            ):
                language = "UKR"
            else:
                language = xml_lang

    if not language:
        parts = xml_path.replace("\\", "/").split("/")

        if len(parts) > 1:
            folder_name = parts[1].upper()
            if folder_name == "RUS":
                path_lower = xml_path.lower()
                if "ukrainian" in path_lower or "українська" in path_lower:
                    language = "UKR"
                else:
                    language = "RUS"
            elif folder_name in [
                "ENG",
                "GER",
                "FRE",
                "ITA",
                "SPA",
                "POR",
                "DAN",
                "SWE",
                "NOR",
                "FIN",
                "NL",
                "POL",
                "HUN",
                "CZE",
                "ROM",
                "RUM",
                "GRE",
                "HEB",
                "ARA",
                "CH",
                "TUR",
                "KOR",
                "JPN",
                "VIE",
                "THA",
                "HIN",
                "TAM",
                "TEL",
                "MAL",
                "KAN",
                "MAR",
                "GUJ",
                "BEN",
                "PAN",
                "TGL",
                "IND",
                "MAY",
                "SW",
                "AMH",
                "ZUL",
                "XHO",
                "SOT",
                "TGK",
                "UZB",
                "KAZ",
                "AZE",
                "PER",
                "URD",
                "PAS",
                "SIN",
                "LKA",
                "NEP",
                "DAR",
                "FAS",
            ]:
                language = folder_name
            language = parts[1]

    books = []
    for book_elem in root.findall("BIBLEBOOK"):
        book_num = int(book_elem.get("bnumber", 0))
        book_name = get_book_name(book_elem, book_num, language)
        chapters = []

        for chap_elem in book_elem.findall("CHAPTER"):
            chap_num = int(chap_elem.get("cnumber", 0))
            verses = []

            for vers_elem in chap_elem.findall("VERS"):
                vers_num = int(vers_elem.get("vnumber", 0))
                vers_text = vers_elem.text or ""
                verses.append((vers_num, vers_text))

            chapters.append((chap_num, verses))

        books.append((book_num, book_name, chapters))

    return bible_name, description, books


def convert_to_spb(xml_path, output_path):
    bible_name, description, books = parse_xml_bible(xml_path)

    abbreviation = "".join(word[0] for word in bible_name.split() if word)

    with open(output_path, "w", encoding="utf-8") as f:
        f.write("##spDataVersion:\t1\n")
        f.write(f"##Title:\t{bible_name}\n")
        f.write(f"##Abbreviation:\t{abbreviation}\n")
        f.write(f"##Information:\t{description}\n")
        f.write("##RightToLeft:\t\n")

        for book_num, book_name, chapters in books:
            f.write(f"{book_num}\t{book_name}\t{len(chapters)}\n")

        f.write("-----\n")

        for book_num, book_name, chapters in books:
            for chap_num, verses in chapters:
                for vers_num, vers_text in verses:
                    verse_id = f"B{book_num:03d}C{chap_num:03d}V{vers_num:03d}"
                    f.write(
                        f"{verse_id}\t{book_num}\t{chap_num}\t{vers_num}\t{vers_text}\n"
                    )

    print(f"Converted '{xml_path}' to '{output_path}'")


def find_xml_files():
    return sorted(glob.glob("**/*.xml", recursive=True))


def pick_option(items, label):
    """Show numbered list, return (selected_items, chose_all) or None to go back."""
    print(f"\n{label}:\n")
    for i, item in enumerate(items, 1):
        print(f"  {i}. {item}")
    print(f"\n  A. All")
    print(f"  B. Back")
    choice = input("\nChoice: ").strip().lower()
    if choice == "b":
        return None
    if choice == "a":
        return (items, True)
    try:
        idx = int(choice) - 1
        if 0 <= idx < len(items):
            return ([items[idx]], False)
    except ValueError:
        pass
    print("Invalid selection.")
    return None


def group_by_dir(files, depth):
    """Group files by directory component at given depth."""
    groups = {}
    for f in files:
        parts = f.replace("\\", "/").split("/")
        if depth < len(parts):
            key = parts[depth]
        else:
            key = os.path.dirname(f)
        groups.setdefault(key, []).append(f)
    return groups


def main():
    all_xml = find_xml_files()

    if not all_xml:
        print("No XML files found in current directory or subdirectories.")
        sys.exit(1)

    # Determine base depth (e.g. "Bibles/ENG/King James Version/file.xml" has parts at 0,1,2,3)
    # Find the top-level directories that contain XML files
    top_dirs = sorted(set(f.replace("\\", "/").split("/")[0] for f in all_xml))

    to_convert = []

    while True:
        # Level 1: top directories (e.g. "Bibles") or root xml files
        top_groups = group_by_dir(all_xml, 0)
        result = pick_option(
            sorted(top_groups.keys()),
            f"Select directory ({len(all_xml)} XML files total)",
        )
        if result is None:
            sys.exit(0)
        selected_top, chose_all = result

        # Collect files from selected top dirs
        level1_files = []
        for t in selected_top:
            level1_files.extend(top_groups.get(t, []))

        if chose_all:
            to_convert = level1_files
            break

        # Level 2: language dirs (e.g. "ENG", "HIN")
        lang_groups = group_by_dir(level1_files, 1)
        if len(lang_groups) <= 1 and len(level1_files) <= 10:
            to_convert = level1_files
            break

        result = pick_option(
            [f"{k} ({len(v)} files)" for k, v in sorted(lang_groups.items())],
            f"Select language/group ({len(level1_files)} XML files)",
        )
        if result is None:
            continue
        selected_langs, chose_all = result

        # Map display strings back to keys
        lang_keys = []
        sorted_langs = sorted(lang_groups.items())
        for sel in selected_langs:
            for k, v in sorted_langs:
                if sel == f"{k} ({len(v)} files)":
                    lang_keys.append(k)
                    break

        level2_files = []
        for k in lang_keys:
            level2_files.extend(lang_groups[k])

        if chose_all:
            to_convert = level2_files
            break

        # Level 3: bible name dirs
        bible_groups = group_by_dir(level2_files, 2)
        if len(bible_groups) <= 1:
            to_convert = level2_files
            break

        result = pick_option(
            [f"{k} ({len(v)} files)" for k, v in sorted(bible_groups.items())],
            f"Select Bible ({len(level2_files)} XML files)",
        )
        if result is None:
            continue
        selected_bibles, chose_all = result

        bible_keys = []
        sorted_bibles = sorted(bible_groups.items())
        for sel in selected_bibles:
            for k, v in sorted_bibles:
                if sel == f"{k} ({len(v)} files)":
                    bible_keys.append(k)
                    break

        level3_files = []
        for k in bible_keys:
            level3_files.extend(bible_groups[k])

        if chose_all:
            to_convert = level3_files
            break

        # Level 4: if multiple files in a bible dir, pick specific file
        if len(level3_files) > 1:
            filenames = [os.path.basename(f) for f in level3_files]
            result = pick_option(
                filenames, f"Select file ({len(level3_files)} XML files)"
            )
            if result is None:
                continue
            selected_files, _ = result
            to_convert = [
                f for f in level3_files if os.path.basename(f) in selected_files
            ]
        else:
            to_convert = level3_files
        break

    if not to_convert:
        print("No files selected.")
        sys.exit(0)

    # Summary and confirmation
    print(f"\n{'=' * 50}")
    print(f"Will convert {len(to_convert)} XML file(s):")
    print(f"{'=' * 50}")
    for f in to_convert:
        print(f"  {f}")
    print()
    confirm = input("Continue? (Y/n): ").strip().lower()
    if confirm and confirm != "y":
        print("Cancelled.")
        sys.exit(0)
    print()

    for xml_file in to_convert:
        base_name = os.path.splitext(xml_file)[0]
        output_file = f"{base_name}.spb"
        convert_to_spb(xml_file, output_file)

    print(f"\nDone! Converted {len(to_convert)} file(s).")


if __name__ == "__main__":
    main()

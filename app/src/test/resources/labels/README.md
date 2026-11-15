# Nutrition label test corpus

Text read from real nutrition-label photos published in public GitHub projects
(M-Strzelecki/OCRResearch, adam-poulton/nutrition-ocr, jkufro/CavEat): UK, US,
Australian, Indian and Middle Eastern packs, some tilted, curved or rotated.

Each photo was scaled to phone-camera size and read offline with Tesseract 5 in two
layouts (`psm11` sparse text, `psm3` page), then grouped into lines the way ML Kit
returns them: `[text, left, top, right, bottom]`. The photos themselves are not stored.

`expected.json` holds the values printed on each label, typed by hand:
`[kcal, protein, fat, carbs]` per 100 g/ml and per serving (`null` = not printed).

`LabelCorpusTest` scores the parser against it; raise its thresholds as the parser improves.

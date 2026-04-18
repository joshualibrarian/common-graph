#!/bin/bash
# Generate PDF from the white paper
# Requires: pandoc, texlive-luatex, texlive-lang-cjk, fonts-noto-color-emoji

cd "$(dirname "$0")"

pandoc the-case.md -o the-case.pdf --pdf-engine=lualatex -V geometry:margin=1in -V fontsize=10pt -H unicode-header.tex

if [ $? -eq 0 ]; then
    PAGES=$(pdfinfo the-case.pdf 2>/dev/null | grep Pages | awk '{print $2}')
    WORDS=$(wc -w < the-case.md)
    echo "Generated docs/the-case.pdf: ${PAGES:-?} pages, ${WORDS} words"
else
    echo "PDF generation failed"
    exit 1
fi

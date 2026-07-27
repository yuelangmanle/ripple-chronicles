
import { Document, Packer, Paragraph, TextRun, ImageRun, Table, TableRow, TableCell, WidthType, AlignmentType, BorderStyle } from 'docx';
import { saveAs } from 'file-saver';

interface ExportImage {
  url: string;
  name: string;
}

export async function exportToWord(images: ExportImage[], filename: string = '浮游动物鉴定报告.docx') {
  const tableRows: TableRow[] = [];
  
  // Process in pairs (2 images per row)
  for (let i = 0; i < images.length; i += 2) {
    const pair = images.slice(i, i + 2);
    
    // Row for Images
    const imageCells = await Promise.all(pair.map(async (img) => {
      try {
        const response = await fetch(img.url);
        const blob = await response.blob();
        const arrayBuffer = await blob.arrayBuffer();
        
        return new TableCell({
          children: [
            new Paragraph({
              alignment: AlignmentType.CENTER,
              children: [
                new ImageRun({
                  data: arrayBuffer,
                  type: "png",
                  transformation: {
                    width: 250,
                    height: 250,
                  },
                }),
              ],
            }),
          ],
          borders: {
            top: { style: BorderStyle.NONE, size: 0, color: "FFFFFF" },
            bottom: { style: BorderStyle.NONE, size: 0, color: "FFFFFF" },
            left: { style: BorderStyle.NONE, size: 0, color: "FFFFFF" },
            right: { style: BorderStyle.NONE, size: 0, color: "FFFFFF" },
          }
        });
      } catch (e) {
        console.error('Failed to load image for export:', e);
        return new TableCell({ children: [new Paragraph('图片加载失败')] });
      }
    }));

    // If odd number of images, add an empty cell
    if (imageCells.length === 1) {
      imageCells.push(new TableCell({ children: [], borders: {
        top: { style: BorderStyle.NONE, size: 0, color: "FFFFFF" },
        bottom: { style: BorderStyle.NONE, size: 0, color: "FFFFFF" },
        left: { style: BorderStyle.NONE, size: 0, color: "FFFFFF" },
        right: { style: BorderStyle.NONE, size: 0, color: "FFFFFF" },
      }}));
    }

    tableRows.push(new TableRow({ children: imageCells }));

    // Row for Names
    const nameCells = pair.map(img => (
      new TableCell({
        children: [
          new Paragraph({
            alignment: AlignmentType.CENTER,
            children: [
              new TextRun({
                text: img.name,
                bold: true,
                size: 24, // 12pt
              }),
            ],
          }),
        ],
        borders: {
          top: { style: BorderStyle.NONE, size: 0, color: "FFFFFF" },
          bottom: { style: BorderStyle.NONE, size: 0, color: "FFFFFF" },
          left: { style: BorderStyle.NONE, size: 0, color: "FFFFFF" },
          right: { style: BorderStyle.NONE, size: 0, color: "FFFFFF" },
        }
      })
    ));

    if (nameCells.length === 1) {
      nameCells.push(new TableCell({ children: [], borders: {
        top: { style: BorderStyle.NONE, size: 0, color: "FFFFFF" },
        bottom: { style: BorderStyle.NONE, size: 0, color: "FFFFFF" },
        left: { style: BorderStyle.NONE, size: 0, color: "FFFFFF" },
        right: { style: BorderStyle.NONE, size: 0, color: "FFFFFF" },
      }}));
    }

    tableRows.push(new TableRow({ children: nameCells }));
    
    // Add an empty row for spacing
    tableRows.push(new TableRow({ 
      children: [
        new TableCell({ children: [new Paragraph('')], borders: {
          top: { style: BorderStyle.NONE, size: 0, color: "FFFFFF" },
          bottom: { style: BorderStyle.NONE, size: 0, color: "FFFFFF" },
          left: { style: BorderStyle.NONE, size: 0, color: "FFFFFF" },
          right: { style: BorderStyle.NONE, size: 0, color: "FFFFFF" },
        }}),
        new TableCell({ children: [new Paragraph('')], borders: {
          top: { style: BorderStyle.NONE, size: 0, color: "FFFFFF" },
          bottom: { style: BorderStyle.NONE, size: 0, color: "FFFFFF" },
          left: { style: BorderStyle.NONE, size: 0, color: "FFFFFF" },
          right: { style: BorderStyle.NONE, size: 0, color: "FFFFFF" },
        }})
      ] 
    }));
  }

  const doc = new Document({
    sections: [{
      properties: {},
      children: [
        new Paragraph({
          text: "浮游动物鉴定报告",
          heading: "Heading1",
          alignment: AlignmentType.CENTER,
        }),
        new Paragraph({ text: "" }),
        new Table({
          width: {
            size: 100,
            type: WidthType.PERCENTAGE,
          },
          rows: tableRows,
        }),
      ],
    }],
  });

  const blob = await Packer.toBlob(doc);
  saveAs(blob, filename);
}

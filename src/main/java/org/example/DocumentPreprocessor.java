//package org.example;
//
//import org.apache.pdfbox.pdmodel.PDDocument;
//import org.apache.pdfbox.text.PDFTextStripper;
//import org.apache.poi.hwpf.extractor.WordExtractor;
//import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
//import org.apache.poi.xwpf.usermodel.XWPFDocument;
//
//import java.io.File;
//import java.io.FileInputStream;
//import java.io.FileWriter;
//import java.io.IOException;
//
//public class DocumentPreprocessor {
//
//    public static void main(String[] args) {
//        if (args.length != 2) {
//            System.err.println("Sử dụng: DocumentPreprocessor <thư_mục_chứa_pdf_doc> <thư_mục_đầu_ra_txt>");
//            System.exit(1);
//        }
//
//        File inputDir = new File(args[0]);
//        File outputDir = new File(args[1]);
//
//        if (!inputDir.exists() || !inputDir.isDirectory()) {
//            System.err.println("Thư mục đầu vào không hợp lệ: " + args[0]);
//            System.exit(1);
//        }
//
//        if (!outputDir.exists()) {
//            outputDir.mkdirs();
//        }
//
//        File[] files = inputDir.listFiles();
//        if (files == null) {
//            System.err.println("Không thể đọc thư mục đầu vào.");
//            System.exit(1);
//        }
//
//        for (File file : files) {
//            if (file.isFile()) {
//                String fileName = file.getName().toLowerCase();
//                try {
//                    String text = "";
//                    if (fileName.endsWith(".pdf")) {
//                        text = extractTextFromPDF(file);
//                        System.out.println("Đã đọc file PDF: " + fileName);
//                    } else if (fileName.endsWith(".docx")) {
//                        text = extractTextFromDocx(file);
//                        System.out.println("Đã đọc file DOCX: " + fileName);
//                    } else if (fileName.endsWith(".doc")) {
//                        text = extractTextFromDoc(file);
//                        System.out.println("Đã đọc file DOC: " + fileName);
//                    } else {
//                        System.out.println("Bỏ qua định dạng không hỗ trợ: " + fileName);
//                        continue;
//                    }
//
//                    // Ghi ra file .txt mới
//                    String outFileName = file.getName() + ".txt";
//                    File outFile = new File(outputDir, outFileName);
//                    try (FileWriter writer = new FileWriter(outFile)) {
//                        writer.write(text);
//                    }
//                    System.out.println("-> Đã lưu thành: " + outFile.getAbsolutePath());
//
//                } catch (Exception e) {
//                    System.err.println("Lỗi khi xử lý file " + file.getName() + ": " + e.getMessage());
//                }
//            }
//        }
//
//        System.out.println("\nHoàn tất! Các file .txt trong thư mục '" + args[1] + "' đã sẵn sàng để đưa vào Hadoop.");
//    }
//
//    private static String extractTextFromPDF(File file) throws IOException {
//        try (PDDocument document = PDDocument.load(file)) {
//            PDFTextStripper stripper = new PDFTextStripper();
//            return stripper.getText(document);
//        }
//    }
//
//    private static String extractTextFromDocx(File file) throws IOException {
//        try (FileInputStream fis = new FileInputStream(file);
//             XWPFDocument doc = new XWPFDocument(fis);
//             XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
//            return extractor.getText();
//        }
//    }
//
//    private static String extractTextFromDoc(File file) throws IOException {
//        try (FileInputStream fis = new FileInputStream(file);
//             WordExtractor extractor = new WordExtractor(fis)) {
//            return extractor.getText();
//        }
//    }
//}

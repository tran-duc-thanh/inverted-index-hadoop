package org.example;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.NullOutputFormat;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

import java.io.ByteArrayInputStream;
import java.io.IOException;

public class DistributedPreprocessor {

    public static class PreprocessorMapper extends Mapper<Text, BytesWritable, NullWritable, NullWritable> {

        private String outputDirPath;

        @Override
        protected void setup(Context context) {
            outputDirPath = context.getConfiguration().get("preprocessor.output.dir");
        }

        @Override
        public void map(Text key, BytesWritable value, Context context) throws IOException, InterruptedException {
            String fileName = key.toString().toLowerCase();
            byte[] fileBytes = value.getBytes();
            int length = value.getLength();
            
            String text = "";
            try (ByteArrayInputStream bais = new ByteArrayInputStream(fileBytes, 0, length)) {
                if (fileName.endsWith(".pdf")) {
                    try (PDDocument document = PDDocument.load(bais)) {
                        PDFTextStripper stripper = new PDFTextStripper();
                        text = stripper.getText(document);
                    }
                } else if (fileName.endsWith(".docx")) {
                    try (XWPFDocument doc = new XWPFDocument(bais);
                         XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
                        text = extractor.getText();
                    }
                } else if (fileName.endsWith(".doc")) {
                    try (WordExtractor extractor = new WordExtractor(bais)) {
                        text = extractor.getText();
                    }
                } else {
                    System.out.println("Bo qua dinh dang khong ho tro: " + fileName);
                    return;
                }
            } catch (Exception e) {
                System.err.println("Loi khi xu ly file " + fileName + ": " + e.getMessage());
                return;
            }

            // Ghi ra file .txt tren HDFS voi ten goc
            String outFileName = key.toString() + ".txt";
            Path outPath = new Path(outputDirPath, outFileName);
            FileSystem fs = outPath.getFileSystem(context.getConfiguration());
            
            try (FSDataOutputStream out = fs.create(outPath, true)) {
                out.write(text.getBytes("UTF-8"));
            }
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Su dung: DistributedPreprocessor <input path> <output path>");
            System.exit(-1);
        }

        Configuration conf = new Configuration();
        conf.set("preprocessor.output.dir", args[1]);
        conf.set("mapreduce.job.user.classpath.first", "true");

        Job job = Job.getInstance(conf, "Distributed Document Preprocessor");
        job.setJarByClass(DistributedPreprocessor.class);

        job.setMapperClass(PreprocessorMapper.class);
        job.setNumReduceTasks(0); // Map-only job

        job.setInputFormatClass(WholeFileInputFormat.class);
        job.setOutputFormatClass(NullOutputFormat.class);

        FileInputFormat.addInputPath(job, new Path(args[0]));
        FileOutputFormat.setOutputPath(job, new Path(args[1]));

        System.exit(job.waitForCompletion(true) ? 0 : 1);
    }
}


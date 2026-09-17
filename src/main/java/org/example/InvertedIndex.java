package org.example;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.FileSplit;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

import java.io.IOException;
import java.util.HashSet;
import java.util.StringTokenizer;

public class InvertedIndex {

    public static class InvertedIndexMapper
            extends Mapper<Object, Text, Text, Text> {

        private Text word = new Text();
        private Text filename = new Text();

        @Override
        public void map(Object key, Text value, Context context) throws IOException, InterruptedException {
            // Lấy tên file của split hiện tại
            FileSplit fileSplit = (FileSplit) context.getInputSplit();
            String fileNameString = fileSplit.getPath().getName();
            filename.set(fileNameString);

            // Tách các từ trong từng dòng
            String line = value.toString().toLowerCase();
            // Xóa các ký tự đặc biệt, chỉ giữ lại chữ cái và số
            line = line.replaceAll("[^\\p{L}\\p{N}\\s]", "");
            
            StringTokenizer itr = new StringTokenizer(line);
            while (itr.hasMoreTokens()) {
                word.set(itr.nextToken());
                context.write(word, filename);
            }
        }
    }

    public static class InvertedIndexReducer
            extends Reducer<Text, Text, Text, Text> {

        private Text result = new Text();

        @Override
        public void reduce(Text key, Iterable<Text> values, Context context)
                throws IOException, InterruptedException {

            // Dùng HashSet để loại bỏ các file trùng lặp
            HashSet<String> fileSet = new HashSet<>();
            for (Text val : values) {
                fileSet.add(val.toString());
            }

            // Gộp danh sách các file thành một chuỗi phân cách bởi dấu phẩy
            StringBuilder sb = new StringBuilder();
            for (String file : fileSet) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(file);
            }
            
            result.set(sb.toString());
            context.write(key, result);
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Sử dụng: InvertedIndex <input path> <output path>");
            System.exit(-1);
        }

        Configuration conf = new Configuration();
        Job job = Job.getInstance(conf, "inverted index");
        job.setJarByClass(InvertedIndex.class);
        
        job.setMapperClass(InvertedIndexMapper.class);
        job.setReducerClass(InvertedIndexReducer.class);
        
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(Text.class);
        
        FileInputFormat.addInputPath(job, new Path(args[0]));
        FileOutputFormat.setOutputPath(job, new Path(args[1]));
        
        System.exit(job.waitForCompletion(true) ? 0 : 1);
    }
}


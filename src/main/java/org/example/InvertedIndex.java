package org.example;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.FileSplit;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.StringTokenizer;

import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.WriteModel;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.bson.Document;

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
            // Thay thế các ký tự đặc biệt bằng dấu cách, chỉ giữ lại chữ cái và số
            line = line.replaceAll("[^\\p{L}\\p{N}\\s]", " ");
            
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
        private MongoClient mongoClient;
        private MongoCollection<Document> collection;
        private List<WriteModel<Document>> bulkOperations;
        private UpdateOptions options;

        @Override
        protected void setup(Context context) throws IOException, InterruptedException {
            super.setup(context);
            // Lấy URI từ Configuration hoặc biến môi trường hoặc tự động phát hiện
            String mongoUri = context.getConfiguration().get("mongo.uri");
            if (mongoUri == null || mongoUri.trim().isEmpty()) {
                mongoUri = System.getenv("MONGO_URI");
            }
            if (mongoUri == null || mongoUri.trim().isEmpty()) {
                // Kiểm tra xem có đang chạy trong container Docker (nhận diện qua host.docker.internal) không
                boolean isDocker = false;
                try {
                    java.net.InetAddress.getByName("host.docker.internal");
                    isDocker = true;
                } catch (Exception ignored) {
                }
                mongoUri = isDocker ? "mongodb://host.docker.internal:27017" : "mongodb://localhost:27017";
            }

            System.out.println("Connecting to MongoDB at: " + mongoUri);
            mongoClient = MongoClients.create(mongoUri);
            MongoDatabase database = mongoClient.getDatabase("inverted_index_db");
            collection = database.getCollection("index_results");
            bulkOperations = new ArrayList<>();
            options = new UpdateOptions().upsert(true);
        }

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
            List<String> filesList = new ArrayList<>();
            for (String file : fileSet) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(file);
                filesList.add(file);
            }
            
            result.set(sb.toString());
            // Vẫn ghi kết quả ra HDFS (nếu cần)
            context.write(key, result);

            // Chuẩn bị operation để ghi vào MongoDB
            String word = key.toString();
            UpdateOneModel<Document> updateModel = new UpdateOneModel<>(
                    Filters.eq("word", word),
                    Updates.addEachToSet("files", filesList),
                    options
            );
            bulkOperations.add(updateModel);

            // Ghi theo batch để tối ưu hiệu suất
            if (bulkOperations.size() >= 1000) {
                collection.bulkWrite(bulkOperations);
                bulkOperations.clear();
            }
        }

        @Override
        protected void cleanup(Context context) throws IOException, InterruptedException {
            // Ghi nốt những operation còn lại trong batch cuối
            if (!bulkOperations.isEmpty()) {
                collection.bulkWrite(bulkOperations);
            }
            // Đóng kết nối MongoDB
            if (mongoClient != null) {
                mongoClient.close();
            }
            super.cleanup(context);
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Sử dụng: InvertedIndex <input path> <output path> [mongo uri]");
            System.exit(-1);
        }

        Configuration conf = new Configuration();
        if (args.length >= 3) {
            conf.set("mongo.uri", args[2]);
        }
        Job job = Job.getInstance(conf, "inverted index");
        job.setJarByClass(InvertedIndex.class);
        
        job.setMapperClass(InvertedIndexMapper.class);
        job.setReducerClass(InvertedIndexReducer.class);
        
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(Text.class);
        
        Path inputPath = new Path(args[0]);
        Path outputPath = new Path(args[1]);

        // Tự động xóa thư mục output nếu đã tồn tại để tránh lỗi FileAlreadyExistsException
        FileSystem fs = outputPath.getFileSystem(conf);
        if (fs.exists(outputPath)) {
            System.out.println("Output directory " + outputPath + " already exists. Deleting it...");
            fs.delete(outputPath, true);
        }

        FileInputFormat.addInputPath(job, inputPath);
        FileOutputFormat.setOutputPath(job, outputPath);
        
        System.exit(job.waitForCompletion(true) ? 0 : 1);
    }
}


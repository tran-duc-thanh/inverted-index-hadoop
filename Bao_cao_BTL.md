# Báo cáo Bài Tập Lớn: Xây dựng Chỉ mục ngược (Inverted Index) trên Hadoop

## 1. Thông tin chung
- **Môn học:** [Tên môn học]
- **Nhóm:** [Số nhóm]
- **Danh sách thành viên:**
  1. [Họ tên thành viên 1] - [MSSV]
  2. [Họ tên thành viên 2] - [MSSV]
  3. [Họ tên thành viên 3] - [MSSV]
  4. [Họ tên thành viên 4] - [MSSV]
  5. [Họ tên thành viên 5] - [MSSV]

## 2. Tìm hiểu lý thuyết MapReduce
MapReduce là một mô hình lập trình và một mô hình thực thi dùng để xử lý và tạo ra các tập dữ liệu lớn bằng một thuật toán song song, phân tán trên một cluster. Một chương trình MapReduce thường bao gồm hai giai đoạn chính:
- **Giai đoạn Map (Ánh xạ):** Nhận đầu vào là các cặp (Key, Value) từ dữ liệu gốc, phân tích và trích xuất thông tin cần thiết, sau đó sinh ra một tập các cặp (Key, Value) trung gian mới.
- **Giai đoạn Reduce (Rút gọn):** Các cặp (Key, Value) trung gian từ giai đoạn Map được nhóm lại theo Key (quá trình Shuffle & Sort). Sau đó hàm Reduce sẽ nhận từng Key và danh sách các Value tương ứng để tổng hợp, tính toán và xuất ra kết quả cuối cùng.

## 3. Thuật toán Inverted Index
Chỉ mục ngược (Inverted Index) là một cấu trúc dữ liệu dùng để ánh xạ các từ vựng đến các tài liệu chứa chúng. Ứng dụng phổ biến nhất là trong các công cụ tìm kiếm, giúp tìm các tài liệu liên quan đến một từ khóa rất nhanh.

Đối với thuật toán MapReduce để xây dựng Inverted Index:
- **Hàm Map:** 
  - Đầu vào (Input): `(byte_offset, Dòng văn bản)`
  - Mỗi khi xử lý một dòng văn bản, ta trích xuất được `filename` (thông qua `context.getInputSplit()`).
  - Phân tách dòng thành các từ (Word).
  - Đầu ra (Output): Emit cặp `(Word, filename)`. Ví dụ: Từ "hadoop" xuất hiện trong "file1.txt" thì output là `("hadoop", "file1.txt")`.

- **Hàm Reduce:**
  - Nhận đầu vào (Input) từ quá trình Shuffle: `(Word, Iterable<filename>)`. Ví dụ: `("hadoop", ["file1.txt", "file1.txt", "file2.txt"])`.
  - Nhiệm vụ của Reducer là gộp danh sách các filename, sử dụng `HashSet` để loại bỏ các filename trùng lặp (ví dụ nếu từ xuất hiện 2 lần trong 1 file thì chỉ lưu 1 lần).
  - Đầu ra (Output): `(Word, Danh_sách_file_đã_gộp)`. Ví dụ: `("hadoop", "file1.txt, file2.txt")`.

## 4. Hướng dẫn chạy và dùng thử nghiệm

### Yêu cầu:
- Java JDK 8 hoặc 11 (Tùy cấu hình).
- Apache Maven (để build).
- Apache Hadoop (để chạy chương trình).

### Bước 1: Build mã nguồn bằng Maven
Mở terminal/cmd tại thư mục gốc của project (nơi chứa file `pom.xml`) và chạy lệnh:
```bash
mvn clean package
```
Kết quả ta sẽ nhận được file JAR tại: `target/inverted-index-hadoop-1.0-SNAPSHOT.jar`.

### Bước 2: Chuẩn bị dữ liệu mẫu
Tạo thư mục `input` trong hệ thống file Hadoop (HDFS) và đưa các file văn bản mẫu lên (hoặc chạy trực tiếp trên Local mode với thư mục input cục bộ).
```bash
# Ví dụ chạy trên HDFS
hadoop fs -mkdir -p /user/hadoop/input
hadoop fs -put input/*.txt /user/hadoop/input
```

### Bước 3: Chạy Job MapReduce
Thực thi lệnh hadoop để chạy class InvertedIndex:
```bash
hadoop jar target/inverted-index-hadoop-1.0-SNAPSHOT.jar org.example.InvertedIndex input output
```
*(Nếu chạy trên HDFS thì thay `input` và `output` thành đường dẫn HDFS tương ứng, ví dụ `/user/hadoop/input /user/hadoop/output`)*

### Bước 4: Xem kết quả
Xem file kết quả sinh ra trong thư mục output (hoặc tải từ HDFS về):
```bash
cat output/part-r-00000
```
Kết quả mong đợi sẽ có dạng:
```
example       file1.txt
hadoop        file1.txt, file2.txt
inverted      file2.txt
mapreduce     file1.txt, file2.txt
```

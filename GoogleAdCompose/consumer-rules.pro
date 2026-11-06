# Rule đi kèm SDK -> tự áp vào mọi app dùng ultra_a_d khi app bật R8. Code của SDK không dùng reflection
# nên không cần keep gì cho com.gsx.**; KHÔNG thêm rule kiểu `-keep class com.gsx.** { *; }` (giữ nguyên tên
# cả package -> tụt điểm "DEX code optimization" trên Play Console).

# WorkManager (AdMob Next-Gen SDK kéo theo) mở Room database của nó bằng reflection qua constructor rỗng của
# WorkDatabase_Impl. R8 full mode (mặc định từ AGP 8) bỏ constructor đó -> app crash RuntimeException ngay lúc
# khởi động. AdMob 1.6.0 đã tự thêm rule này; giữ ở đây để app ghim AdMob cũ hơn vẫn an toàn.
-keep class * extends androidx.room.RoomDatabase { <init>(); }

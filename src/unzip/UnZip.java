package unzip;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * @author jeffrey
 * @ClassName: UnZip
 * @Description: 为偷懒而生，解压一个目录包括子目录中的所有 zip 文件
 * @date: 2021/1/9 12:38 上午
 * @version:
 * @since JDK 1.8
 */


public class UnZip {

    /**
     * 用于存放于 zip 文件的安全队列
     */
    public final static ConcurrentLinkedQueue<File> ZIP_QUEUE = new ConcurrentLinkedQueue<>();

    /**
     * 如 t1 不存活并 zipQueue 为空则修改为 true
     */
    public static boolean isFlag = false;

    /**
     * 输入流
     */
    private static final Scanner SCANNER = new Scanner(System.in);

    /**
     * 提供一个工作路径
     */
    private static String workPath;

    /**
     * 在解压完成后是否删除源文件
     *      使用 Boolean 包装类进行判断，True / true 为 true，False / false 为 false，其他依然为 false
     */
    public static String isDelete;

    /**
     * 在解压文件时发生异常的集合
     *      Key：文件名
     *      Value：异常对象
     */
    public static final HashMap<File, Exception> unZipFailedMap = new HashMap<>();

    /**
     * 找到相关的文件数量
     */
    public static int foundCount = 0;

    /**
     * 成功解压的文件数量
     */
    public static int succeedCount = 0;

    /**
     * 解压失败的文件数量
     */
    public static int unZipFailedCount = 0;

    /**
     * 删除失败的文件个数
     */
    public static int deleteFailedCount = 0;

    /**
     * 删除失败的文件名
     */
    public static final HashSet<File> deleteFailedNameList = new HashSet<>();


    public static void main(String[] args) {

        input();

        if (new File(workPath).exists()) {

                Thread t1 = new Thread(new FindZipFile(new File(workPath)));
                Thread t2 = new Thread(new UnZipFile(ZIP_QUEUE));
                t1.start();
                t2.start();

                while (true) {
                    if (!t1.isAlive() && ZIP_QUEUE.isEmpty()) {
                        isFlag = true;
                        if (!t2.isAlive()) {
                            System.out.println
                            (
                                    "\n\n\n" +
                                    "共找到了 " + foundCount + " 个文件\n" +
                                    "成功解压了 " + succeedCount + " 个文件\n" +
                                            unZipFailedCount + " 个文件解压失败，以下是失败原因："
                            );
                            Set<Map.Entry<File, Exception>> failedElement = unZipFailedMap.entrySet();
                            for (Map.Entry<File, Exception> fileExceptionEntry : failedElement) {
                                System.out.println("文件：" + fileExceptionEntry.getKey() + " 异常：" + fileExceptionEntry.getValue());
                            }

                            if (deleteFailedCount > 0) {
                                System.out.println("另外，还有一些文件删除失败，可能是在解压过程中失败了导致它们不被删除，它们是：");
                                for (File file : deleteFailedNameList) {
                                    System.out.println(file);
                                }
                            }

                            break;
                        }
                    }
            }
        }else{
            System.out.println("提供的工作路径不准确");
        }
    }

    private static void input() {
        System.out.println("提供一个工作路径：");
        workPath = SCANNER.nextLine();

        System.out.println("成功解压完成后是否删除源文件（true / false）：");
        isDelete = SCANNER.nextLine();
    }
}

class UnZipFile extends Thread {
    private final ConcurrentLinkedQueue<File> zipQueue;
    private static final int  BUFFER_SIZE = 2 * 1024;

    public UnZipFile(ConcurrentLinkedQueue<File> zipQueue) {
        this.zipQueue = zipQueue;
    }

    @Override
    public void run() {
        do {
            if (!zipQueue.isEmpty()) {
                File file = zipQueue.poll();
                String fileName = file.getName();
                File destPathName = new File(file.getParent(), fileName.substring(0, fileName.indexOf(".")));
                System.out.println(destPathName.mkdirs());
                unZip(file, destPathName.toString());
            }
        } while (!UnZip.isFlag);
    }

    public static void unZip(File srcFile, String destDirPath) throws RuntimeException {
        long start = System.currentTimeMillis();
        // 开始解压
        ZipFile zipFile = null;
        try {
            zipFile = new ZipFile(srcFile, Charset.forName("GBK"));
            Enumeration<?> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = (ZipEntry) entries.nextElement();
                System.out.println("解压" + entry.getName());
                // 如果是文件夹，就创建个文件夹
                if (entry.isDirectory()) {
                    String dirPath = destDirPath + File.separator + entry.getName();
                    File dir = new File(dirPath);
                    if(dir.mkdirs()){
                        System.out.println("No need to create");
                    }
                } else {
                    // 如果是文件，就先创建一个文件，然后用io流把内容copy过去
                    File targetFile = new File(destDirPath + File.separator + entry.getName());
                    // 保证这个文件的父文件夹必须要存在
                    if(!targetFile.getParentFile().exists()){
                        if(targetFile.getParentFile().mkdirs()){
                            System.out.println("No need to create");
                        }
                    }
                    if (targetFile.createNewFile()){
                        System.out.println("No need to create");
                    }
                    // 将压缩文件内容写入到这个文件中
                    InputStream is = zipFile.getInputStream(entry);
                    FileOutputStream fos = new FileOutputStream(targetFile);
                    int len;
                    byte[] buf = new byte[BUFFER_SIZE];
                    while ((len = is.read(buf)) != -1) {
                        fos.write(buf, 0, len);
                    }
                    fos.close();
                    is.close();
                    
                    if (Boolean.parseBoolean(UnZip.isDelete)){
                        if(srcFile.delete()){
                            System.out.println("成功删除：" + srcFile);
                        }else{
                            UnZip.deleteFailedNameList.add(srcFile);
                            UnZip.deleteFailedCount++;
                        }
                    }
                }
            }
            long end = System.currentTimeMillis();
            UnZip.succeedCount++;
            System.out.println("解压完成，耗时：" + (end - start) +" ms");
        } catch (Exception e) {
            UnZip.unZipFailedCount++;
            UnZip.unZipFailedMap.put(srcFile, e);
        } finally {
            if(zipFile != null){
                try {
                    zipFile.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}

class FindZipFile extends Thread {
    private final File file;

    public FindZipFile(File file) {
        this.file = file;
    }

    @Override
    public void run() {
        find(file);
    }

    public void find(File path){
        File[] files = path.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()){
                    find(file);
                }else{
                    UnZip.foundCount++;
                    String absolutePath = file.getAbsolutePath();
                    String[] split = absolutePath.split("\\.");
                    if ("zip".equalsIgnoreCase(split[split.length - 1])){
                        UnZip.ZIP_QUEUE.add(file);
                    }
                }
            }
        }
    }
}
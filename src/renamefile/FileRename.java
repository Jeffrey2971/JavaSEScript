package renamefile;

import java.io.File;
import java.util.*;


/**
 * @author jeffrey
 * @ClassName: FileRename
 * @Description: 批量文件重命名
 * @date: 2021/5/23 6:50 下午
 * @version:
 * @since JDK 1.8
 */


public class FileRename {

    private static final String[] fileType = new String[]{"avi", "wmv", "mp4", "pdf"};
    private static final Scanner SCANNER = new Scanner(System.in);
    private static final List<File> errorFile = new ArrayList<>();
    private static String keyword;
    private static String fill;
    private static int successCount;
    private static int errorCount;
    private static int foundCount;

    private static boolean renameDirectory;
    private static final List<File> directoryList = new ArrayList<>();


    public static void main(String[] args) {
        System.out.println("输入填补关键字：");
        fill = SCANNER.nextLine();
        System.out.println("输入一个工作路径：");
        File workPath = new File(SCANNER.nextLine());
        System.out.println("输入被替换的文件关键字");
        keyword = SCANNER.next();
        System.out.println("请输入是否重命名文件夹，默认为 false");
        renameDirectory = Boolean.parseBoolean(SCANNER.next());


        if (workPath.isDirectory()) {

            findAndRename(workPath);
            renameDirectory();

            System.out.println("\n\n----------\n" +
                    "找到：" + foundCount + " 个文件\n" +
                    "成功重命名：" + successCount + " 个文件\n" +
                    "重命名失败：" + errorCount + " 个文件");

            if (!errorFile.isEmpty()) {
                for (File file : errorFile) {
                    System.out.println(file);
                }
            }

        } else {
            throw new RuntimeException("指定的工作路径不存在");
        }
    }

    private static void renameDirectory() {
        // 将数组反转，确保从后往前命名
        Collections.reverse(directoryList);

        for (File file : directoryList) {
            File absoluteFile = file.getAbsoluteFile();
            String fileName = file.getName();

            if (file.renameTo(new File(absoluteFile.getParent() + File.separator + fileName.replace(keyword, fill)))){
                System.out.println(true);
            }
        }


    }

    private static void findAndRename(File workPath) {

        File[] files = workPath.listFiles();

        if (files != null) {
            for (File file : files) {

                String fileName = file.getName();
                if (file.isDirectory()) {
                    if (renameDirectory) {
                        if (fileName.contains(keyword) && !directoryList.contains(file)) {
                            directoryList.add(file);
                        }
                    }
                    findAndRename(file);
                } else {
                    String absoluteFilePath = file.toString();

                    if (fileName.contains(keyword)) {
                        String[] split = absoluteFilePath.split("\\.");
                        if (Arrays.asList(fileType).contains(split[split.length - 1])) {
                            foundCount++;
                            System.out.println("正在处理：" + file);
                            String newAbsoluteFilePath = file.getParent() + File.separator + fileName.replace(keyword, fill);
                            if (file.renameTo(new File(newAbsoluteFilePath))) {
                                successCount++;
                            } else {
                                errorFile.add(file);
                                errorCount++;
                            }
                        }
                    }
                }
            }
        }
    }
}
package threadcopy;

import java.io.*;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Scanner;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * @author jeffrey
 * @ClassName: ThreadCopy
 * @Description: 多线程文件复制移动，未测试，不确保安全性
 * @date: 2020/10/18 3:12 上午
 * @version:
 * @since JDK 1.8
 */

public class ThreadCopy {
    private static final ConcurrentLinkedQueue<File> QUEUE = new ConcurrentLinkedQueue<File>();
    private static final Scanner SCANNER = new Scanner(System.in);

    public static String[] types;
    public static File workPath;
    public static File savePath;
    public static int copyErrorCount;
    public static int copySuccessCount;
    public static boolean isFlag;
    public static int findCount;
    public static String mode;
    public static String DIFF;
    public static int nameSameFile;
    public static int nameAndContentSameFile;
    public static int renameFileCount;

    public static void main(String[] args) {
        input();
        if (types.length != 0) {
            if ("1".equals(mode) || "2".equals(mode) || "3".equals(DIFF)) {
                if (workPath.exists() && savePath.exists()) {
                    Thread t1 = new Thread(new ThreadForFindFiles(QUEUE, workPath, types));
                    Thread t2 = new Thread(new ThreadForCopyFile(QUEUE, savePath));
                    t1.start();
                    t2.start();

                    while (true) {
                        if (!t1.isAlive() && QUEUE.isEmpty()) {
                            isFlag = true;
                        }

                        if (!t1.isAlive() && !t2.isAlive()) {
                            if ("1".equals(mode)) {
                                mode = "复制";
                            } else {
                                mode = "移动";
                            }
                            System.out.println(
                                    "执行完毕，以下是程序执行相关数据：\n" +
                                    "\t\t\t工作路径：" + workPath + "\n" +
                                    "\t\t\t存储路径：" + savePath + "\n" +
                                    "\t\t\t操作文件的类型：" + Arrays.toString(types) + "\n" +
                                    "\t\t\t发现匹配的文件：" + findCount + "\n" +
                                    "\t\t\t发现重名但内容不同的文件：" + nameSameFile + "\n" +
                                    "\t\t\t发现重名但内容相同文件：" + nameAndContentSameFile + "\n" +
                                    "\t\t\t已重命名的文件数：" + renameFileCount + "\n" +
                                    "\t\t\t" + copyErrorCount + " 个文件" + mode + "失败\n" +
                                    "\t\t\t" + copySuccessCount + " 个文件" + mode + "成功"
                            );
                            break;
                        }
                    }
                } else {
                    throw new RuntimeException("请输入有效地工作路径或存储路径");
                }
            } else {
                throw new RuntimeException("请具体指定在程序遇到同名但不同内容的文件时应该做出的操作");
            }
        } else {
            throw new RuntimeException("请至少输入一个需要筛选的文件类型");
        }
    }


    /**
     * @description: 读取键盘输入
     * @author: jeffrey
     * @date: 2020/12/21
     * @return: void
     * @params:
     */
    private static void input(){
        System.out.println("请输入一个工作路径：");
        workPath = new File(SCANNER.nextLine());

        System.out.println("请输入一个存储路径：");
        savePath = new File(SCANNER.nextLine());

        System.out.println("请选择要操作文件模式的序号（1：复制 2：移动）：");
        mode = SCANNER.nextLine();

        System.out.println("请选择当程序遇到同名但内容不同时的操作（1、跳过 2、覆盖 3、生成副本）：");
        DIFF = SCANNER.nextLine();

        System.out.println("请输入筛选文件的类型，如有多个使用逗号隔开：");
        types = SCANNER.nextLine().replace(" ", "").split(",");
    }
}

/**
 * 该线程采用递归方式筛选指定文件，并存入 ConcurrentLinkedQueue 数据队列中
 */
class ThreadForFindFiles extends Thread {

    private final ConcurrentLinkedQueue<File> queue;
    private final File srcPath;
    private final String[] fileTypes;

    public ThreadForFindFiles(ConcurrentLinkedQueue<File> queue, File srcPath, String[] fileTypes) {
        this.queue = queue;
        this.srcPath = srcPath;
        this.fileTypes = fileTypes;
    }

    @Override
    public void run() {
        find(this.srcPath);
    }

    private void find(File srcPath) {
        File[] files = srcPath.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    find(file);
                } else {
                    String[] split = file.toString().split(File.separator);
                    String[] suffix = split[split.length - 1].split("\\.");
                    if (Arrays.toString(fileTypes).contains(suffix[suffix.length - 1])) {
                        ThreadCopy.findCount++;
                        this.queue.add(file);
                    }
                }
            }
        }
    }
}

/**
 * 该线程循环从数据队列中 poll 一个数据进行操作
 */
class ThreadForCopyFile extends Thread {
    private final ConcurrentLinkedQueue<File> queue;
    private final File savePath;
    private static final HashMap<String, String> MD5_MAP = new HashMap<>();
    private static final String DIFF = ThreadCopy.DIFF;
    private static final HashMap<String, Integer> NAME_COUNT_MAP = new HashMap<>();

    public ThreadForCopyFile(ConcurrentLinkedQueue<File> queue, File savePath) {
        this.queue = queue;
        this.savePath = savePath;
    }

    @Override
    public void run() {
        while (true) {
            BufferedInputStream bis = null;
            BufferedOutputStream bos = null;
            File file = null;
            Integer nameCount = null;
            try {
                if (!queue.isEmpty()) {
                    file = queue.poll();
                    if (file != null) {
                        String[] split = file.toString().split(File.separator);
                        String name = split[split.length - 1];
                        if (!MD5_MAP.containsKey(name)) {
                            // 当前文件不在集合中，放入集合
                            MD5_MAP.put(name, getMd5(file));
                        } else {
                            /*
                                当前文件名在 HashMap 中，则判断它们的 md5 值是否相同
                                    相同：表示两个文件名以及内容完全一致
                                    不同：文件名相同，但内容不同
                            */
                            String oldMd5Value = MD5_MAP.get(name);
                            String newMd5Value = getMd5(file);

                            if (oldMd5Value.equals(newMd5Value)) {
                                /*
                                    该分支处理文件名以及内容都相同时的逻辑
                                 */
                                ThreadCopy.nameAndContentSameFile++;
                                continue;
                            } else {
                                /*
                                    该分支处理遇到重名文件但内容不同时的逻辑
                                        1、跳过
                                        2、覆盖
                                        3、另存为副本
                                 */
                                if ("1".equals(DIFF)){
                                    continue;
                                }else if ("3".equals(DIFF)) {
                                    /*
                                        获取文件名的后缀名
                                     */
                                    String[] suffixList = name.split("//.");
                                    String suffix = suffixList[suffixList.length - 1];
                                    /*
                                        判断文件名是否在集合中
                                     */
                                    if (NAME_COUNT_MAP.containsKey(name)) {
                                        // 存在，获取其出现的次数，并在原有值的基础上 ++
                                        nameCount = NAME_COUNT_MAP.get(name);
                                        NAME_COUNT_MAP.put(name, nameCount++);
                                    } else {
                                        // 不存在，添加在文件名并设置其对应的值为 1
                                        NAME_COUNT_MAP.put(name, 1);
                                        nameCount = 1;
                                    }
                                    /*
                                        重新拼装重名文件
                                     */
                                    name = name + name.replace(suffix, "") + "的重名文件" + nameCount + "." + suffix;
                                    ThreadCopy.nameSameFile++;
                                    ThreadCopy.renameFileCount++;
                                }else if (!"2".equals(DIFF)){
                                    System.out.println("数据异常");
                                    break;
                                }
                            }
                        }
                        /*
                            此处代码块为处理文件的复制、移动
                                1、复制
                                2、移动
                         */
                        if ("1".equals(ThreadCopy.mode)) {
                            /*
                                该分支处理文件的赋值
                             */
                            bis = new BufferedInputStream(new FileInputStream(file));
                            bos = new BufferedOutputStream(new FileOutputStream(savePath + File.separator + name));

                            byte[] bytes = new byte[1024];
                            int len;
                            while ((len = bis.read(bytes)) != -1) {
                                bos.write(bytes, 0, len);
                            }
                            ThreadCopy.copySuccessCount++;
                        } else {
                            if (file.renameTo(new File(savePath + File.separator + name))) {
                                ThreadCopy.copySuccessCount++;
                            } else {
                                ThreadCopy.copyErrorCount++;
                            }
                        }
                    }
                }
                /*
                    队列为空且线程1已经死亡则终止该线程
                 */
                if (ThreadCopy.isFlag) {
                    break;
                }
            } catch (Exception e) {
                /*
                    遇到无法操作的文件时不退出
                 */
                ThreadCopy.copyErrorCount++;
                System.out.println("处理文件" + file + "失败，该文件的资源可能被占用或程序无法读取它，以下是详细信息：\n");
                e.printStackTrace();
            } finally {
                /*
                    关闭流资源
                 */
                if (bis != null) {
                    try {
                        bis.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                if (bos != null) {
                    try {
                        bos.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }


    /**
     * 该方法用户获取文件 md5 值
     * @param path 文件路径
     * @return md5
     */
    public static String getMd5(File path) {
        FileInputStream fis = null;
        try {
            byte[] buffer = new byte[8192];
            int len;
            MessageDigest md = MessageDigest.getInstance("MD5");
            fis = new FileInputStream(path);
            while ((len = fis.read(buffer)) != -1) {
                md.update(buffer, 0, len);
            }
            byte[] bytes = md.digest();
            return new BigInteger(1, bytes).toString();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return null;
    }
}



package com.yulin.videoRecording.controller;

import com.yulin.videoRecording.doman.VideoPaths;
import com.yulin.videoRecording.doman.model.AjaxResult;
import com.yulin.videoRecording.utils.test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 两个摄像头先录制后合成
 */
@RestController
public class RecorderManager {

    private static final Logger logger = LoggerFactory.getLogger(test.class);
    @Value("${yulin.camera1}")
    private String videoDevice1;

    @Value("${yulin.camera2}")
    private String videoDevice2;

    //    @Value("${yulin.audioDevice}")
    private final String audioDevice = "麦克风阵列 (Realtek(R) Audio)";

    @Value("${yulin.filePath}")
    private String filePath;
    private ProcessBuilder processBuilder1;
    private ProcessBuilder processBuilder2;
    private Process process1;
    private Process process2;
    private BufferedWriter writer1;
    private BufferedWriter writer2;
    private Thread outputThread1;
    private Thread outputThread2;
    private boolean isRecording = false;


    /**
     * 开始录制
     */
    @GetMapping("/startVideo")
    public AjaxResult startVideo(boolean start) {
        if (start) {
            if (!isRecording) {
                VideoPaths recorder = Recorder();
                startRecording();
                logger.info("开始录制");
                return AjaxResult.success("开始录制", recorder);
            } else {
                return AjaxResult.error("已经在录制中");
            }
        } else {
            if (isRecording) {
                stopRecording();
                logger.info("录制结束");
                return AjaxResult.success("录制结束");
            } else {
                return AjaxResult.error("请先开始录制");
            }
        }
    }

    // 初始化录制器
    public VideoPaths Recorder() {

        DateTimeFormatter sdf = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
        String L = LocalDateTime.now().format(sdf) + "L" + ".flv";
        String R = LocalDateTime.now().format(sdf) + "R" + ".flv";
        String outputFileName1 = filePath + L;
        String outputFileName2 = filePath + R;

        // 左摄像头
        String ffmpegCmd1 = "ffmpeg -f dshow -i video=\"" + videoDevice1 + "\":audio=\"" + audioDevice + "\" -c:v libx264 -preset ultrafast -c:a aac " + outputFileName1;
        processBuilder1 = new ProcessBuilder("cmd.exe", "/c", ffmpegCmd1);
        processBuilder1.redirectErrorStream(true);

        // 右摄像头
        String ffmpegCmd2 = "ffmpeg -f dshow -i video=\"" + videoDevice2 + "\":audio=\"" + audioDevice + "\" -c:v libx264 -preset ultrafast -c:a aac " + outputFileName2;
        processBuilder2 = new ProcessBuilder("cmd.exe", "/c", ffmpegCmd2);
        processBuilder2.redirectErrorStream(true);

        // 设置文件输出名
        VideoPaths videoPaths = new VideoPaths();
        videoPaths.setVideoL(L);
        videoPaths.setVideoR(R);

        logger.info("录制文件名：" + L + " " + R);
        return videoPaths;
    }

    // 开始录制
    public void startRecording() {
        try {
            process1 = processBuilder1.start();
            writer1 = new BufferedWriter(new OutputStreamWriter(process1.getOutputStream()));

            // 读取FFmpeg输出的线程
            outputThread1 = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process1.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null && isRecording) {
                        System.out.println(line);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });

            process2 = processBuilder2.start();
            writer2 = new BufferedWriter(new OutputStreamWriter(process2.getOutputStream()));

            // 读取FFmpeg输出的线程
            outputThread2 = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process2.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null && isRecording) {
                        System.out.println(line);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });

            outputThread1.setDaemon(true);
            outputThread2.setDaemon(true);
            outputThread1.start();
            outputThread2.start();
            isRecording = true;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 结束录制
    public void stopRecording() {
        try {
            if (isRecording) {
                writer1.write("q");
                writer2.write("q");
                writer1.flush();
                writer2.flush();
                writer1.close();
                writer2.close();

                // 等待进程结束
                int exitCode1 = process1.waitFor();
                int exitCode2 = process2.waitFor();

                if (exitCode1 == 0 || exitCode2 == 0) {
                    logger.info("录制成功");
                } else {
                    logger.error("录制失败");
                }

                isRecording = false;
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * 合并视频
     * @param videoL
     * @param videoR
     * @return
     */
    @GetMapping("/mergeVideos")
    public AjaxResult mergeVideos(String videoL, String videoR){

        String inputPath1 = filePath + videoL;
        String inputPath2 = filePath + videoR;

        // 视频文件路径
        DateTimeFormatter sdf = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
        String outputFileName = LocalDateTime.now().format(sdf) + ".flv";
        String outputPath = filePath + outputFileName;//合成后的文件存储路径和名称

        // 构建FFmpeg命令
//        String command = String.format("ffmpeg -i \"%s\" -i \"%s\" -filter_complex \"[0:v][1:v]hstack\" \"%s\"", leftVideoPath, rightVideoPath, outputPath);
        String command = String.format("ffmpeg -i \"%s\" -i \"%s\" -filter_complex \"[1:v]scale=-1:720[v1];[0:v][v1]hstack=inputs=2\" -c:a copy \"%s\"", inputPath1, inputPath2, outputPath);

        try {
            // 运行FFmpeg命令
            Process process = Runtime.getRuntime().exec(command);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()));

            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }

            int exitCode = process.waitFor();
            if (exitCode == 0) {
                logger.info("视频合并完成，保存至: " + outputPath);
                // 视频合成成功后，删除原始视频文件
                File leftVideoFile = new File(inputPath1);
                File rightVideoFile = new File(inputPath2);
                leftVideoFile.delete();
                rightVideoFile.delete();
            } else {
                logger.error("视频合并失败，退出码: " + exitCode);
                return AjaxResult.error("视频合并失败，退出码: " + exitCode);
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
        return AjaxResult.success("视频合并完成",outputFileName);
    }

    /**
     * 下载视频
     * @param outputFileName
     * @return
     */
    @GetMapping("/download/{outputFileName}")
    public ResponseEntity<byte[]> downloadFile(@PathVariable String outputFileName) {
        try {
            // 指定文件路径，这里假设文件存储在当前运行目录下的 files 文件夹内
            Path filePath1 = Paths.get(filePath, outputFileName);

            // 设置响应头，指定文件名和内容类型
            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + outputFileName);
            headers.add(HttpHeaders.CONTENT_TYPE, Files.probeContentType(filePath1));

            // 读取文件内容并返回给客户端
            byte[] fileContent = Files.readAllBytes(filePath1);
            return ResponseEntity.ok().headers(headers).body(fileContent);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }
}


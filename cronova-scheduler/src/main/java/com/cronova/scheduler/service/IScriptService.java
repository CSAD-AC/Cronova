package com.cronova.scheduler.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.cronova.scheduler.domain.po.Script;
import com.cronova.scheduler.domain.vo.ScriptVO;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * 脚本服务接口
 */
public interface IScriptService extends IService<Script> {

    void clearCache(String scriptId);

    /**
     * 获取脚本列表
     * @return 脚本列表
     */
    List<Script> list();

    /**
     * 上传脚本文件到MinIO
     * @param file 脚本文件
     * @return 脚本信息
     */
    ScriptVO uploadScript(MultipartFile file);

    /**
     * 上传文件夹（ZIP压缩包）到MinIO
     * @param file ZIP压缩包文件
     * @param mainFile 主入口文件相对路径（如 src/main.py）
     * @return 脚本信息
     */
    ScriptVO uploadDirectory(MultipartFile file);

    /**
     * 下载脚本文件
     * @param id 脚本ID
     * @param response 响应对象
     * @throws IOException IO异常
     */
    void downloadScript(String id, HttpServletResponse response) throws IOException;

    /**
     * 下载文件夹（ZIP压缩包）
     * @param id 脚本ID（文件夹类型）
     * @param response 响应对象
     * @throws IOException IO异常
     */
    void downloadDirectory(String id, HttpServletResponse response) throws IOException;

    /**
     * 删除脚本及其对应的MinIO文件
     * @param id 脚本ID
     * @return 是否成功
     */
    boolean deleteScript(String id);

    /**
     * 更新脚本内容
     * @param id 脚本ID
     * @param content 脚本内容
     * @return 是否成功
     */
    boolean updateScriptContent(String id, String content);
}

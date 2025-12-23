package com.sjdt.scheduler.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.sjdt.scheduler.domain.po.Script;
import com.sjdt.scheduler.domain.vo.ScriptVO;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 脚本服务接口
 */
public interface IScriptService extends IService<Script> {

    /**
     * 上传脚本文件到MinIO
     * @param file 脚本文件
     * @return 脚本信息
     */
    ScriptVO uploadScript(MultipartFile file);

    /**
     * 下载脚本文件
     * @param id 脚本ID
     * @param response 响应对象
     * @throws IOException IO异常
     */
    void downloadScript(String id, HttpServletResponse response) throws IOException;
}

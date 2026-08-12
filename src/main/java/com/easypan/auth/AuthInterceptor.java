package com.easypan.auth;

import com.easypan.exception.BusinessException;
import com.easypan.mapper.SysUserMapper;
import com.easypan.model.entity.SysUser;
import com.easypan.model.enums.DataStatus;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Objects;

/**
 * JWT身份认证拦截器
 * 拦截受保护接口，校验请求携带的Bearer Token，完成用户身份解析、合法性校验，将当前登录用户存入线程上下文
 * 请求处理完毕后清理线程上下文，避免线程复用造成用户信息串扰
 */
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtService jwtService;
    private final SysUserMapper userMapper;

    /**
     * Controller执行之前执行的预处理方法
     * @param request Http请求对象
     * @param response Http响应对象
     * @param handler 处理器对象，代表即将执行的controller方法
     * @return true：放行请求；false：拦截请求；本项目校验失败直接抛出业务异常
     */
    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler
    ){
        // 放行跨域OPTIONS预检请求，OPTIONS请求没有业务token，直接放过
        if("OPTIONS".equalsIgnoreCase(request.getMethod()))
            return true;

        // 获取请求头Authorization，标准Bearer token存放位置
        String authorization=request.getHeader("Authorization");
        // 判断请求头是否存在，并且以Bearer 开头，Bearer后面有一个空格，总前缀长度7
        if(authorization==null||!authorization.startsWith("Bearer "))
            throw new BusinessException(400,"请求头缺少Bearer Token");

        // 截取真实token字符串，去掉"Bearer "前缀
        String token =authorization.substring(7);
        // Jwt工具解析token，拿到token中存储的用户ID，token过期、篡改会直接抛出异常
       JwtIdentity identity=jwtService.parse(token);
        // 根据用户ID查询数据库，获取用户完整信息
        SysUser user=userMapper.selectById(identity.userId());

        // 用户为空，或者用户状态不是激活状态，拒绝访问，返回403权限禁止
        if(user==null||!DataStatus.ACTIVE.name().equals(user.getStatus()))
            throw new BusinessException(403,"用户不存在或已被禁用");
        //null表示：1.用户主动退出登录；2.管理员重置了密码；3.管理员主动清除了登录状态
        if(user.getCurrentSessionId()==null)
            throw new BusinessException(401,"登录状态已失效，请重新登录");
        //数据库sessionId和JWT中的sid不用，说明这个账号在登陆以后又发生了一次新的登录
        if(!Objects.equals(user.getCurrentSessionId(),identity.sessionId()))
            throw new BusinessException(401,"账号已在其他设备登录，请重新登录");
        // 将数据库查询出来的SysUser转换为当前登录用户上下文对象，存入ThreadLocal
        UserContext.set(CurrentUser.from(user, identity.sessionId()));
        // 校验全部通过，放行接口访问
        return true;
    }

    /**
     * 请求完成之后回调，无论接口正常执行还是抛出异常，都会执行该方法
     * 核心作用：清除ThreadLocal保存的用户信息，防止线程池线程复用时，旧用户信息泄露到下一次请求
     * @param request Http请求对象
     * @param response Http响应对象
     * @param handler 处理器对象
     * @param exception 整个请求链路抛出的异常，没有异常则为null
     */
    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception exception
    ){
        // 清空线程本地存储的登录用户信息，必须执行，防止内存泄漏、用户上下文错乱
        UserContext.clear();
    }

}
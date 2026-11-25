package com.driedfish.project_psychology_health_assist.util;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.driedfish.project_psychology_health_assist.common.DTO.internal.TokenVerifyResult;
import com.driedfish.project_psychology_health_assist.common.ResultCode;
import com.driedfish.project_psychology_health_assist.common.exception.BusinessException;
import com.driedfish.project_psychology_health_assist.config.JwtConfig;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Date;
import java.util.concurrent.TimeUnit;
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtTokenUtil {
    private static final String ISSUER = "mental_health_assist";
    //黑名单key前缀
    private static final String TOKEN_BLACKLIST_PREFIX = "jwt:blacklist:";
    private final StringRedisTemplate stringRedisTemplate;
    private final JwtConfig jwtConfig;
    public String generateJwtToken(Long userId,String username, Integer roleType) {
        try{
            //生成签名算法
            Algorithm algorithm=Algorithm.HMAC256(jwtConfig.getSecret());
            //过期时间
            Date expiration=new Date(System.currentTimeMillis()+jwtConfig.getExpiration());
            //JWT.create()方法创建jwt token(java-jwt)上一个项目中使用的是jjwt库,方法名不同
            String token=JWT.create()
                    .withClaim("userId",userId)
                    .withClaim("username",username)
                    .withClaim("roleType",roleType)
                    .withExpiresAt(expiration)
                    .withIssuedAt(new Date())
                    .withIssuer(ISSUER)
                    .sign(algorithm);
            return token;}
        catch (Exception e){
            log.error("token生成失败,请检查jwt配置是否正确",e);
            throw new BusinessException(ResultCode.SYSTEM_ERROR);
        }


    }
    // 提取token
    public String extractTokenFromRequest(HttpServletRequest request) {
        if(request==null){
            return null;
        }
        String tokenHeader = request.getHeader(jwtConfig.getHeader());
        String prefix=jwtConfig.getTokenPrefix();
        if(StringUtils.hasText(tokenHeader)){
            if(StringUtils.hasText(prefix)&&tokenHeader.startsWith(prefix)){
                return tokenHeader.substring(prefix.length());
            }
            return tokenHeader;
        }
        return null;
    }
    //验证token
    public TokenVerifyResult.result validateToken(String token){
        try{DecodedJWT decodedJWT=verifyToken(token);
            if(decodedJWT==null){
                return new TokenVerifyResult.result(null,null,null,false);
            }
            String username=decodedJWT.getClaim("username").asString();
            Long userId=decodedJWT.getClaim("userId").asLong();
            Integer roleType=null;
            try{
                roleType=decodedJWT.getClaim("roleType").asInt();
            }catch (Exception e){
                String roleTypeStr = decodedJWT.getClaim("roleType").asString();
                if(StringUtils.hasText(roleTypeStr)){
                    roleType=Integer.valueOf(roleTypeStr);
                }
            }
            if(roleType!=null&&StringUtils.hasText(username)&&userId!=null){
                return new TokenVerifyResult.result(username,userId,roleType,true);
            }
            return new TokenVerifyResult.result(null,null,null,false);}
        catch (Exception e){
            return new TokenVerifyResult.result(null,null,null,false);
        }


    }
        //获取当前token
    public String getCurrentToken(){
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if(attributes!=null){
            HttpServletRequest request=attributes.getRequest();
            String token=(String) request.getAttribute("jwtToken");
            if(StringUtils.hasText(token)){
            return token;}
            //从请求头中获取token
            String headerToken=extractTokenFromRequest(request);
            if(StringUtils.hasText(headerToken)){
                return headerToken;
            }
        return null;
        }
        return null;
    }
        //验证token有效性
    public DecodedJWT verifyToken(String token){
        if(!StringUtils.hasText(token)){
            throw new JWTVerificationException("token不能为空");
        }
        //解密token,获取token中的信息
        Algorithm algorithm=Algorithm.HMAC256(jwtConfig.getSecret());
        JWTVerifier verifier=JWT.require(algorithm)
                .withIssuer(ISSUER)
                .build();
        return verifier.verify(token);

    }
    //注销token:把token写入Redis黑名单,过期时间与token剩余有效期一致
    public void invalidateToken(String token) {
        if (!StringUtils.hasText(token)) {
            throw new JWTVerificationException("token不能为空");
        }
        DecodedJWT decodedJWT = verifyToken(token);
        long expireAt = decodedJWT.getExpiresAt() == null
                ? System.currentTimeMillis() + jwtConfig.getExpiration()
                : decodedJWT.getExpiresAt().getTime();
        long ttl = expireAt - System.currentTimeMillis();
        if (ttl <= 0) {
            //token已过期,无需写入黑名单
            return;
        }
        stringRedisTemplate.opsForValue()
                .set(TOKEN_BLACKLIST_PREFIX + token, "1", ttl, TimeUnit.MILLISECONDS);
    }

    //判断token是否已被注销(黑名单中存在)
    public boolean isTokenBlocked(String token) {
        if (!StringUtils.hasText(token)) {
            return false;
        }
        return Boolean.TRUE.equals(
                stringRedisTemplate.hasKey(TOKEN_BLACKLIST_PREFIX + token));
    }


}

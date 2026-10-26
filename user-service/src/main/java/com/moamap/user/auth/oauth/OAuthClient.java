package com.moamap.user.auth.oauth;

// 카카오/구글 공통 인터페이스
public interface OAuthClient {

    String getProvider();

    OAuthUserInfo getUserInfo(String providerAccessToken);
}

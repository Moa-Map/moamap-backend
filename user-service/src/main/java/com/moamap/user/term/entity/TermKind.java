package com.moamap.user.term.entity;

/**
 * 약관의 성격. 같은 약관(code)의 버전이 올라가도 바뀌지 않는다.
 */
public enum TermKind {

    /** 동의해야 서비스를 이용할 수 있다. 앱은 체크박스로 보여준다. */
    REQUIRED,

    /** 동의 없이 보기만 한다(개인정보 처리방침 등). 앱은 "보기" 링크로 보여준다. */
    VIEW_ONLY
}

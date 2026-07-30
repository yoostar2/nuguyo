# Room 이 생성한 구현체와 엔티티는 리플렉션으로 접근되므로 유지한다.
-keep class com.nuguyo.app.data.db.** { *; }

# 시스템이 이름으로 바인딩하는 컴포넌트들.
-keep class com.nuguyo.app.service.** { *; }

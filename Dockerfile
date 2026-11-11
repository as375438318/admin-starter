# 后端镜像（单容器）。前端为静态产物，不在本文件范围内。
#
# 构建：docker build -t admin-starter:local .
# 运行：docker run --rm -p 8080:8080 \
#         -e APP_DB_URL='jdbc:mysql://host:3306/admin_starter?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false' \
#         -e APP_DB_USERNAME=admin_app -e APP_DB_PASSWORD=*** \
#         -e SPRING_DATA_REDIS_HOST=redis -e SPRING_DATA_REDIS_PASSWORD=*** \
#         -v /data/admin-starter/uploads:/app/data/uploads \
#         admin-starter:local
#
# 说明：
# - 连接信息一律走环境变量（与 application.yml 的 ${APP_DB_URL:...} 等占位符一致），镜像内不含任何口令。
# - 上传目录必须挂载卷，否则容器重建后文件字节丢失（数据库里的记录会指向不存在的文件）。
# - 非 root 运行，降低容器逃逸后的影响面。

FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
# 先只拷 pom，利用层缓存避免每次改代码都重新下载依赖
COPY pom.xml ./
COPY server/pom.xml server/pom.xml
COPY server/common/pom.xml server/common/pom.xml
COPY server/system/pom.xml server/system/pom.xml
COPY server/auth/pom.xml server/auth/pom.xml
COPY server/web/pom.xml server/web/pom.xml
RUN mvn -B -q -pl web -am dependency:go-offline || true
COPY server ./server
RUN mvn -B -pl web -am -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
RUN groupadd --system --gid 1001 app \
    && useradd --system --uid 1001 --gid app --home /app app \
    && mkdir -p /app/data/uploads \
    && chown -R app:app /app
COPY --from=build --chown=app:app /workspace/server/web/target/admin-starter.jar /app/app.jar
USER app
EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -Dfile.encoding=UTF-8"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]

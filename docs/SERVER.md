# 예소 운영 서버 관리 가이드 (EC2 + SSH)

> 최초 작성: 2026-10-13
> 최종 수정: 2026-10-31 — 앞단에 Caddy 를 두어 https 를 열었다 (`https://52-79-82-17.sslip.io`). 기존 http 주소도 그대로 동작한다.
> 대상: 운영 서버에 SSH 로 접속해서 터미널로 배포 · 확인 · 재시작하는 사람
> 범위: 서버 한 대에 앱 · DB · Redis · Caddy 를 전부 올린 지금 구성. 자동 배포(CD)는 쓰지 않는다.

---

## 1. 서버 정보

| 항목 | 값 |
|---|---|
| 이름 | `khu-artistic-sw` |
| 인스턴스 ID | `i-077f0a88951ab80a0` |
| 리전 | `ap-northeast-2` (서울) |
| 사양 | t3.micro (vCPU 2, 메모리 1GB, 프리티어) + 스왑 2GB |
| OS | Ubuntu 24.04, 서버 시간대 UTC |
| 디스크 | gp3 20GB |
| 공인 IP | `52.79.82.17` (Elastic IP, 인스턴스를 껐다 켜도 바뀌지 않는 고정 IP) |
| 보안 그룹 | `khu-artistic-sw-sg`: 22(SSH) · 80(HTTP) · 443(HTTPS) 을 전체(0.0.0.0/0)에 열어 둠 |
| SSH 키 | `~/.ssh/khu-artistic-sw-key.pem` (AWS 키 페어 이름 `khu-artistic-sw-key`) |
| 로그인 계정 | `ubuntu` |

### BASE_URL

```
https://52-79-82-17.sslip.io   (https, 권장)
http://52.79.82.17             (http, 기존 앱 · 기기용으로 그대로 유지)
```

- 앱: `.../api/v1/...`
- 기기: `.../device-api/v1/...`
- 헬스체크: `.../actuator/health`
- `sslip.io` 는 주소 안에 적힌 IP 로 그대로 연결해 주는 무료 이름 서비스다. 도메인을 사지 않고 https 인증서(Let's Encrypt)를 받으려고 쓴다. IP 가 바뀌면 이 이름도 바뀐다.
- 브라우저에서 도는 페이지(GitHub Pages 의 가상 기기 등, https)는 http 서버를 부를 수 없다(혼합 콘텐츠 차단). 반드시 https 주소를 쓴다.
- `http://52-79-82-17.sslip.io` 로 들어오면 https 로 넘겨준다(308). `http://52.79.82.17` 은 넘기지 않고 그대로 응답한다.

> SSH 키 파일(`.pem`)은 저장소에 올리지 않는다. 키를 잃어버리면 이 서버에 SSH 로 다시 들어갈 방법이 없다. 팀원과 나눌 때는 메신저가 아닌 별도의 안전한 경로를 쓴다.

---

## 2. 접속

```bash
ssh -i ~/.ssh/khu-artistic-sw-key.pem ubuntu@52.79.82.17
```

- `-i` 는 로그인에 쓸 키 파일을 지정한다. EC2 는 비밀번호 대신 이 키로 본인 확인을 한다.
- 키 파일 권한이 너무 열려 있으면 SSH 가 키를 거부한다. 처음 받았을 때 한 번 `chmod 400 ~/.ssh/khu-artistic-sw-key.pem` 을 실행한다 (소유자만 읽기 가능).
- 접속하지 않고 명령 하나만 보내고 싶으면 뒤에 따옴표로 붙인다.

```bash
ssh -i ~/.ssh/khu-artistic-sw-key.pem ubuntu@52.79.82.17 'docker ps'
```

---

## 3. 서버 안의 구성

### 3-1. 폴더

```
/home/ubuntu/yeso/
├── compose.yaml   컨테이너 4개의 정의
├── Caddyfile      https 앞단(caddy) 설정
├── app.jar        로컬에서 빌드해 올린 실행 파일
└── .env           DB 비밀번호 (권한 600, 저장소에 없음)
```

### 3-2. 컨테이너

```
EC2 (Ubuntu)
└── Docker
    ├── yeso-caddy-1     caddy:2. EC2 80 · 443 을 받아 app:8080 으로 넘긴다
    │                    → https 인증서를 자동으로 받고 갱신한다 (caddy-data 볼륨에 저장)
    ├── yeso-app-1       eclipse-temurin:21-jre 이미지 + ~/yeso/app.jar 연결
    │                    → 안에서 java -jar /app/app.jar 실행. 바깥 포트 없음, 내부 8080
    ├── yeso-postgres-1  postgres:17, 데이터는 postgres-data 볼륨에 저장
    └── yeso-redis-1     redis:8 (현재 앱 코드는 쓰지 않음, 4-3 참고)
```

- EC2 에는 Java 를 따로 설치하지 않았다. Java 는 app 컨테이너 안에만 있다.
- jar 를 실행하는 것은 Docker 엔진이다. 컨테이너를 켜면 `compose.yaml` 의 `command:` 에 적힌 `java -jar` 를 컨테이너 안의 1번 프로세스로 실행한다. 이 프로세스가 끝나면 컨테이너도 꺼진다.
- `restart: unless-stopped` 이므로 앱이 죽거나 EC2 가 재부팅되면 Docker 가 알아서 다시 켠다. 사람이 `stop` 으로 멈춘 경우만 예외다.

### 3-3. compose.yaml

```yaml
services:
  postgres:
    image: postgres:17
    restart: unless-stopped
    environment:
      POSTGRES_DB: yeso
      POSTGRES_USER: yeso
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
    volumes:
      - postgres-data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U yeso -d yeso"]
      interval: 5s
      timeout: 3s
      retries: 20
  redis:
    image: redis:8
    restart: unless-stopped
  app:
    image: eclipse-temurin:21-jre
    restart: unless-stopped
    working_dir: /app
    command: ["java", "-Xms128m", "-Xmx384m", "-jar", "/app/app.jar"]
    volumes:
      - ./app.jar:/app/app.jar:ro
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/yeso
      SPRING_DATASOURCE_USERNAME: yeso
      SPRING_DATASOURCE_PASSWORD: ${POSTGRES_PASSWORD}
      SPRING_DATA_REDIS_HOST: redis
      SPRING_DATA_REDIS_PORT: "6379"
    # 바깥 포트는 caddy 가 받는다. 앱은 compose 내부망에서만 8080 으로 열린다.
    expose:
      - "8080"
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_started
  caddy:
    image: caddy:2
    restart: unless-stopped
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./Caddyfile:/etc/caddy/Caddyfile:ro
      - caddy-data:/data
      - caddy-config:/config
    depends_on:
      - app
volumes:
  postgres-data:
  caddy-data:
  caddy-config:
```

### 3-4. Caddyfile

```
52-79-82-17.sslip.io {
	reverse_proxy app:8080
}

:80 {
	reverse_proxy app:8080
}
```

| 블록 | 뜻 |
|---|---|
| `52-79-82-17.sslip.io` | 이 이름으로 오는 요청은 https 로 받는다. 인증서는 Caddy 가 처음 켜질 때 받고, 만료 전에 알아서 갱신한다 |
| `:80` | 그 밖의 http 요청(`http://52.79.82.17`)은 https 로 넘기지 않고 그대로 앱에 전달한다. 기존 앱과 실제 기기가 이 주소를 쓰고 있어서다 |
| `caddy-data` 볼륨 | 받은 인증서를 보관한다. 지우면 다시 받아야 하고, 너무 자주 받으면 Let's Encrypt 발급 한도에 걸린다 |

- CORS(브라우저 호출 허용)는 Caddy 가 아니라 앱(`SecurityConfiguration`)이 처리한다. 허용 출처는 `API.md` 1-1-2.
- Caddyfile 을 고친 뒤에는 `docker compose restart caddy` 로 다시 읽힌다.

| 설정 | 뜻과 이유 |
|---|---|
| `command:` | Dockerfile 의 CMD 와 같은 역할이며, 이미지에 들어 있던 CMD 를 덮어쓴다. compose 는 이미지를 "만드는" 파일이 아니라 "띄우는" 파일이라 RUN 은 없다 |
| `expose` (app) | EC2 바깥이 아니라 같은 compose 안의 컨테이너(caddy)에게만 포트를 연다. 앱에 직접 붙지 못하게 하고 모든 요청이 caddy 를 거치게 한다 |
| `-Xmx384m` | Java 가 쓸 최대 메모리. 서버 메모리가 1GB 뿐이라 DB 몫을 남기려고 상한을 걸었다 |
| `./app.jar:/app/app.jar:ro` | 바인드 마운트. EC2 의 파일을 컨테이너 안에 그대로 보이게 연결한다(복사가 아니다). `ro` 는 읽기 전용 |
| `postgres-data:...` | 이름 붙은 볼륨. Docker 가 관리하는 저장 공간이라 컨테이너를 지우고 다시 만들어도 DB 데이터가 남는다 |
| `postgres:5432` | 같은 compose 안의 컨테이너끼리는 IP 대신 서비스 이름으로 서로를 찾는다 |
| `${POSTGRES_PASSWORD}` | 같은 폴더의 `.env` 에서 값을 읽는다. 비밀번호를 compose 파일에 직접 적지 않으려고 분리했다 |
| `depends_on ... service_healthy` | DB 가 접속을 받을 준비가 된 뒤에 앱을 켠다. 앱이 먼저 떠서 DB 연결에 실패하는 것을 막는다 |

---

## 4. 자주 하는 작업

> 4장의 명령은 별도 표시가 없으면 **서버에 접속한 뒤 `~/yeso` 폴더에서** 실행한다.
> `cd ~/yeso` 를 먼저 한다. `docker compose` 는 현재 폴더의 `compose.yaml` 을 읽기 때문이다.

### 4-1. 재배포 (코드를 고친 뒤)

**내 PC 의 저장소 루트에서** 실행한다.

```bash
./gradlew bootJar
```

```bash
scp -i ~/.ssh/khu-artistic-sw-key.pem build/libs/artistic-software-khu-0.0.1-SNAPSHOT.jar ubuntu@52.79.82.17:~/yeso/app.jar
```

```bash
ssh -i ~/.ssh/khu-artistic-sw-key.pem ubuntu@52.79.82.17 'cd ~/yeso && docker compose restart app'
```

1. `bootJar` 로 실행 파일을 만든다. 테스트는 돌지 않으므로 배포 전에 `./gradlew test` 를 따로 돌려 초록불을 확인한다.
2. `scp` 는 SSH 를 통해 파일을 복사한다. 서버의 `app.jar` 를 덮어쓴다.
3. app 컨테이너만 재시작한다. postgres 와 redis 는 그대로이므로 데이터에 영향이 없다.

- 빌드는 반드시 로컬에서 한다. 메모리 1GB 서버에서 Gradle 을 돌리면 메모리가 모자라 멈출 가능성이 크다.
- 2번과 3번 사이에는 실행 중인 앱 밑에서 jar 파일이 바뀐 상태라 잠깐 오류가 날 수 있다. 2번이 끝나면 3번을 바로 실행한다.
- 재시작 후 앱이 다시 뜨기까지 약 20초가 걸린다. 그동안은 요청이 실패한다.
- DB 마이그레이션(`db/migration` 의 새 SQL)은 앱이 켜질 때 Flyway 가 자동으로 적용한다.

### 4-2. 상태 확인

```bash
# 헬스체크 (어디서든 실행 가능). {"status":"UP"} 이면 정상
curl -i http://52.79.82.17/actuator/health

# 컨테이너 목록과 상태. postgres 는 (healthy) 가 붙어 있어야 정상
docker ps

# 앱 로그 마지막 200줄
docker compose logs --tail 200 app

# 앱 로그 실시간으로 보기 (Ctrl+C 로 빠져나옴, 앱은 계속 돈다)
docker compose logs -f app

# 컨테이너 안에서 돌고 있는 프로세스 (java -jar 한 줄이 보여야 함)
docker top yeso-app-1

# 메모리와 스왑 사용량
free -h

# 컨테이너별 CPU 와 메모리 사용량 (Ctrl+C 로 빠져나옴)
docker stats

# 디스크 사용량
df -h /
```

### 4-3. 켜기 · 끄기 · 재시작

```bash
# 전체 켜기. 컨테이너가 없으면 새로 만들고, compose.yaml 이 바뀌었으면 반영해서 다시 만든다
docker compose up -d

# 앱만 재시작 (jar 교체 후)
docker compose restart app

# 전체 멈추기. 컨테이너는 남아 있고 재부팅해도 자동으로 켜지지 않는다
docker compose stop

# 전체 내리기. 컨테이너는 지우지만 DB 볼륨(postgres-data)은 남는다
docker compose down
```

> `docker compose down -v` 는 **절대 실행하지 않는다.** `-v` 는 볼륨까지 지우는 옵션이라 DB 데이터가 전부 사라진다.

- `compose.yaml` 이나 `.env` 를 고쳤을 때는 `restart` 가 아니라 `up -d` 를 실행한다. `restart` 는 컨테이너를 껐다 켜기만 하고 바뀐 설정을 읽지 않는다.
- Redis: 지금 앱 코드는 Redis 를 쓰지 않는다. 그래도 `spring-boot-starter-data-redis` 의존성이 남아 있어서, Redis 컨테이너가 없으면 헬스체크가 `DOWN` 으로 나온다. 그래서 띄워 둔다.

### 4-4. DB 직접 보기

```bash
# psql 접속 (\q 로 빠져나옴)
docker compose exec postgres psql -U yeso -d yeso

# 적용된 마이그레이션 목록
docker compose exec postgres psql -U yeso -d yeso -c "select version, description, success from flyway_schema_history order by installed_rank;"
```

- `exec` 는 이미 떠 있는 컨테이너 안에서 명령을 하나 실행한다.
- 컨테이너 안에서 접속하므로 비밀번호를 묻지 않는다. DB 포트(5432)는 EC2 바깥으로 열려 있지 않다.
- 운영 DB 이므로 `update` 나 `delete` 는 실행 전에 반드시 `select` 로 대상 행부터 확인한다.

### 4-5. DB 백업과 복원

```bash
# 백업: 서버의 ~/yeso 에 날짜가 붙은 파일로 저장
docker compose exec -T postgres pg_dump -U yeso -d yeso > ~/yeso/backup-$(date +%Y%m%d-%H%M).sql

# 백업 파일을 내 PC 로 가져오기 (내 PC 에서 실행, 파일 이름은 실제 이름으로 바꾼다)
scp -i ~/.ssh/khu-artistic-sw-key.pem ubuntu@52.79.82.17:~/yeso/backup-20261013-1500.sql .

# 복원 (빈 DB 에 넣을 때)
docker compose exec -T postgres psql -U yeso -d yeso < ~/yeso/backup-20261013-1500.sql
```

- `-T` 는 터미널 모드를 끄는 옵션이다. 출력을 파일로 보내거나 파일을 입력으로 넣을 때 필요하다.
- 자동 백업은 설정되어 있지 않다. 위험한 작업을 하기 전에는 손으로 백업한다.

### 4-6. DB 비밀번호 확인

```bash
cat ~/yeso/.env
```

- 이 파일은 서버에만 있다. 서버를 새로 만들 때는 `umask 077; echo "POSTGRES_PASSWORD=$(openssl rand -hex 24)" > .env` 로 새로 만든다 (`umask 077` 은 새 파일을 소유자만 읽을 수 있게 만든다).
- 이미 DB 가 만들어진 뒤에 `.env` 의 비밀번호만 바꾸면 앱이 DB 에 접속하지 못한다. Postgres 는 비밀번호를 볼륨을 처음 만들 때 한 번만 읽기 때문이다.

---

## 5. 문제가 생겼을 때

| 증상 | 먼저 볼 것 | 흔한 원인과 조치 |
|---|---|---|
| `curl` 결과가 `000` (연결 안 됨) | `docker ps` | app 컨테이너가 꺼져 있거나 아직 뜨는 중. 20초 기다린 뒤 다시 확인하고, 그래도 안 되면 `docker compose logs --tail 200 app` |
| app 컨테이너가 계속 `Restarting` | `docker compose logs --tail 200 app` | 로그 맨 아래의 `APPLICATION FAILED TO START` 를 찾는다. 흔한 원인은 Flyway 마이그레이션 실패와 엔티티-테이블 불일치(`ddl-auto=validate` 가 기동을 막음) |
| health 가 `DOWN` | `docker ps` 에서 postgres · redis 상태 | 기본 설정이라 health 응답에는 무엇이 `DOWN` 인지 나오지 않는다. 대부분 DB 나 Redis 컨테이너가 꺼진 경우다. `docker compose up -d` |
| 응답이 갑자기 느려짐 · 앱이 죽음 | `free -h`, `docker stats` | 메모리 부족. 스왑을 많이 쓰고 있으면 느려진다. 해결책은 인스턴스를 t3.small(2GB) 로 올리는 것 |
| 디스크 부족 | `df -h /`, `docker system df` | 오래된 백업 파일과 쓰지 않는 이미지를 지운다. `docker image prune` 은 쓰지 않는 이미지만 지운다 |
| SSH 가 `Permission denied (publickey)` | 키 파일 경로, 계정 이름 | 계정은 `ubuntu` 다. 키 파일 권한이 400 인지 확인한다 |
| SSH 가 `REMOTE HOST IDENTIFICATION HAS CHANGED` | | 서버를 새로 만들어 같은 IP 를 붙인 경우. 옛 기록을 지운다: `ssh-keygen -R 52.79.82.17` |

---

## 6. 서버 자체 관리

```bash
# EC2 재부팅 (컨테이너는 자동으로 다시 뜬다)
sudo reboot

# OS 보안 업데이트
sudo apt update && sudo apt upgrade -y
```

AWS 콘솔 대신 **내 PC 에서 AWS CLI 로** 인스턴스를 켜고 끌 수 있다.

```bash
aws ec2 stop-instances --instance-ids i-077f0a88951ab80a0
```

```bash
aws ec2 start-instances --instance-ids i-077f0a88951ab80a0
```

- 인스턴스를 꺼도 Elastic IP 와 디스크 요금은 계속 나간다. 완전히 정리하려면 7장의 리소스를 모두 지워야 한다.

---

## 7. 만들어 둔 AWS 리소스

| 종류 | 이름 / ID |
|---|---|
| EC2 인스턴스 | `khu-artistic-sw` / `i-077f0a88951ab80a0` |
| 보안 그룹 | `khu-artistic-sw-sg` / `sg-08966d56ccae3c493` |
| 키 페어 | `khu-artistic-sw-key` |
| Elastic IP | `52.79.82.17` / `eipalloc-0c58d5e0acd0644d4` |
| EBS 볼륨 | `khu-artistic-sw` 태그, 20GB gp3 |

인스턴스를 처음 만들 때 user-data(인스턴스가 처음 켜질 때 한 번 실행되는 스크립트)로 아래 작업을 했다. 서버를 새로 만들 때 같은 작업을 다시 하면 된다.

```bash
# 스왑 2GB 만들기 (메모리 1GB 로는 앱 + DB 가 빠듯하므로)
fallocate -l 2G /swapfile && chmod 600 /swapfile && mkswap /swapfile && swapon /swapfile
echo '/swapfile none swap sw 0 0' >> /etc/fstab

# Docker 설치 (Docker Compose 플러그인 포함)
curl -fsSL https://get.docker.com | sh
usermod -aG docker ubuntu   # ubuntu 계정이 sudo 없이 docker 를 쓰게 한다
```

---

## 8. 알아 둘 한계

- **http 주소도 계속 열려 있다.** `http://52.79.82.17` 로 오는 요청은 암호화되지 않는다. 앱과 기기를 https 주소로 옮기면 Caddyfile 의 `:80` 블록을 지워 http 를 닫을 수 있다.
- **https 주소가 IP 에 묶여 있다.** `sslip.io` 이름은 IP 를 그대로 담으므로, Elastic IP 를 바꾸면 주소도 바뀌고 인증서도 새로 받는다.
- **SSH(22)가 전체에 열려 있다.** 키 인증이라 뚫리지는 않지만 무작위 접속 시도가 계속 들어온다.
- **자동 배포가 없다.** 배포는 4-1 을 손으로 실행한다.
- **자동 백업이 없다.** 4-5 를 손으로 실행한다.
- **서버가 한 대뿐이다.** 재배포 중이거나 서버가 죽으면 그동안 서비스가 멈춘다.

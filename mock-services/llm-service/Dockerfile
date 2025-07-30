FROM golang:1.21-alpine AS builder

WORKDIR /app
COPY main.go .

RUN go mod init mock-llm-service
RUN go build -o mock-llm-service main.go

FROM alpine:latest

RUN apk --no-cache add ca-certificates
WORKDIR /root/

COPY --from=builder /app/mock-llm-service .

EXPOSE 8081

CMD ["./mock-llm-service"]
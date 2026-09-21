# Local development only. MinIO's community distribution is source-only.
# Official releases: https://github.com/minio/minio/releases
#                   https://github.com/minio/mc/releases
FROM golang:1.25-alpine AS build-base
ENV CGO_ENABLED=0
RUN apk add --no-cache ca-certificates git

FROM build-base AS build-server
RUN go install -p 2 -trimpath github.com/minio/minio@RELEASE.2025-10-15T17-29-55Z
RUN mkdir /licenses && cp /go/pkg/mod/github.com/minio/minio@*/LICENSE /licenses/LICENSE

FROM build-base AS build-client
RUN go install -p 2 -trimpath github.com/minio/mc@RELEASE.2025-08-13T08-35-41Z
RUN mkdir /licenses && cp /go/pkg/mod/github.com/minio/mc@*/LICENSE /licenses/LICENSE

FROM alpine:3.22 AS runtime
RUN apk add --no-cache ca-certificates && addgroup -S storage && adduser -S storage -G storage
WORKDIR /home/storage
USER storage

FROM runtime AS server
COPY --from=build-server /go/bin/minio /usr/local/bin/minio
COPY --from=build-server /licenses /licenses
USER root
RUN mkdir /data && chown storage:storage /data
USER storage
EXPOSE 9000 9001
ENTRYPOINT ["minio"]
CMD ["server", "/data", "--console-address", ":9001"]

FROM runtime AS client
COPY --from=build-client /go/bin/mc /usr/local/bin/mc
COPY --from=build-client /licenses /licenses
ENTRYPOINT ["mc"]

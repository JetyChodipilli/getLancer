FROM node:22.23.3-alpine3.23
WORKDIR /opt/demo-publisher
COPY --chown=node:node server.mjs ./server.mjs
RUN mkdir /data && chown node:node /data
USER node
ENV DEMO_PUBLISHER_DATA_DIR=/data DEMO_PUBLISHER_PORT=8081 DEMO_PUBLISHER_HOST=0.0.0.0
EXPOSE 8081
HEALTHCHECK --interval=15s --timeout=3s --start-period=5s --retries=3 CMD node -e 'const http=require("node:http");const req=http.get({host:"127.0.0.1",port:process.env.DEMO_PUBLISHER_PORT, path:"/health",headers:{Host:process.env.DEMO_PUBLISHER_ADMIN_HOST}},r=>{r.resume();process.exitCode=r.statusCode===200?0:1});req.setTimeout(2000,()=>req.destroy());req.on("error",()=>{process.exitCode=1})'
CMD ["node", "server.mjs"]

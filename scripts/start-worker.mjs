import {createBuiltWorker,runtimeBindings} from './worker-runtime.mjs';
const portIndex=process.argv.indexOf('--port');
const port=Number(portIndex===-1?process.env.PORT||3000:process.argv[portIndex+1]);
if(!Number.isInteger(port)||port<0||port>65535)throw new Error('Invalid server port.');
const runtime=createBuiltWorker({bindings:runtimeBindings(),host:process.env.HOST||'127.0.0.1',port});
console.log(`Worker listening at ${await runtime.ready}`);
for(const signal of ['SIGINT','SIGTERM'])process.once(signal,async()=>{await runtime.dispose();process.exit(0);});

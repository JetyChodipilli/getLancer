export function simulate(){return [18,24,31,29,35].map((temperature,index)=>({tick:index,topic:'simulated/temperature',temperature,unit:'C',alert:temperature>=30,simulated:true}));}
if(process.argv[1]&&new URL(import.meta.url).pathname===process.argv[1])console.log(JSON.stringify(simulate(),null,2));

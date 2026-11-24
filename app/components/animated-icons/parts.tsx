import type {Variants} from 'motion/react';
import * as m from 'motion/react-m';

/* Locally adapted SVG paths and animation variants, with shorter finite motion.
 * pqoqubbw/icons @ 4662fff2b60fdcf4751b32013606756caf7c4c26 (MIT):
 * icons/{home,blocks,graduation-cap,upload,users,circle-help}.tsx
 * Avijit07x/animateicons @ 49b3c297b4fa3364e9c24f5208ce6c22aa35bac8 (MIT):
 * icons/lucide/{flask-conical,bookmark,search,menu,code-xml,
 * layout-dashboard,log-in}-icon.tsx
 * Underlying Lucide ISC and Feather MIT notices accompany the project notices.
 * Shared controls replace upstream per-icon divs/imperative wrappers. SVG-root
 * variants move onto internal groups so labels and icon boxes never move.
 */
export type NavIconName='home'|'components'|'college'|'learning'|'publish'|'templates'|'teams'|'saved'|'help'|'workspace'|'signin'|'search'|'menu'|'briefcase'|'shield'|'recruitment'|'delivery'|'activity';

const draw:Variants={normal:{pathLength:1,opacity:1},animate:{pathLength:[0,1],opacity:[0,1],transition:{duration:.42,opacity:{duration:.16},ease:'easeInOut'}}};
const blocks:Variants={normal:{x:0,y:0},animate:{x:[0,-2,0],y:[0,2,0],transition:{duration:.44,ease:'easeInOut'}}};
const cap:Variants={normal:{y:0,rotate:0},animate:{y:[0,-1.3,0],rotate:[0,-2,2,0],transition:{duration:.48,ease:'easeInOut'}}};
const tassel:Variants={normal:{rotate:0},animate:{rotate:[0,10,-6,3,0],transition:{duration:.52,delay:.04,ease:'easeInOut'}}};
const flask:Variants={normal:{rotate:0},animate:{rotate:[0,-6,5,-3,0],transition:{duration:.5,ease:'easeInOut'}}};
const liquid:Variants={normal:{rotate:0},animate:{rotate:[0,6,-4,2,0],transition:{duration:.5,delay:.03,ease:'easeInOut'}}};
const bookmark:Variants={normal:{y:0,scaleX:1,scaleY:1},animate:{y:[0,-2,0],scaleY:[1,1.06,.98,1],scaleX:[1,.98,1.01,1],transition:{duration:.42,ease:'easeOut'}}};
const users:Variants={normal:{x:0},animate:{x:[0,-3,0],transition:{duration:.46,ease:'easeInOut'}}};
const upload:Variants={normal:{y:0},animate:{y:[0,-2,0],transition:{duration:.42,ease:'easeInOut'}}};
const help:Variants={normal:{rotate:0},animate:{rotate:[0,-6,6,-6,0],transition:{duration:.44,ease:'easeInOut'}}};
const lens:Variants={normal:{x:0,y:0,rotate:0},animate:{x:[0,1,-1,.5,0],y:[0,-.5,1,-.5,0],rotate:[0,4,-4,2,0],transition:{duration:.5,ease:'easeInOut'}}};
const tiles:Variants={normal:{scale:1},animate:(i:number)=>({scale:[1,.88,1.03,1],transition:{duration:.38,ease:'easeInOut',times:[0,.35,.75,1],delay:i*.035}})};
const arrow:Variants={normal:{x:0},animate:{x:[0,2,-.5,0],transition:{duration:.42,ease:'easeInOut',times:[0,.4,.75,1]}}};
const leftCode:Variants={normal:{x:0},animate:{x:[0,-1,.3,0],transition:{duration:.42,ease:'easeInOut',times:[0,.35,.7,1]}}};
const rightCode:Variants={normal:{x:0},animate:{x:[0,1,-.3,0],transition:{duration:.42,ease:'easeInOut',times:[0,.35,.7,1]}}};
const slash:Variants={normal:{rotate:0},animate:{rotate:[0,7,-2,0],transition:{duration:.42,ease:'easeInOut',times:[0,.35,.7,1]}}};
const bars=(scale:number,delay:number):Variants=>({normal:{scaleX:1},animate:{scaleX:[1,scale,1],transition:{duration:.38,ease:'easeInOut',delay}}});

export function IconParts({name}:{name:NavIconName}){
 switch(name){
  // pqoqubbw/icons/icons/home.tsx: draw the doorway within the steady outline.
  case 'home':return <><path d="M3 10a2 2 0 0 1 .709-1.528l7-5.999a2 2 0 0 1 2.582 0l7 5.999A2 2 0 0 1 21 10v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/><m.path data-animated-part="doorway" d="M15 21v-8a1 1 0 0 0-1-1h-4a1 1 0 0 0-1 1v8" variants={draw}/></>;
  case 'components':return <><path d="M10 21V8a1 1 0 0 0-1-1H4a1 1 0 0 0-1 1v12a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1v-5a1 1 0 0 0-1-1H3"/><m.path data-animated-part="block" d="M14 3h7v7h-7z" variants={blocks}/></>;
  case 'college':return <m.g data-animated-part="cap" style={{transformOrigin:'12px 12px'}} variants={cap}><path d="M2 10l10-5 10 5-10 5z"/><path d="M6 12v5c3 3 9 3 12 0v-5"/><m.path data-animated-part="tassel" d="M22 10v6" style={{transformBox:'fill-box',transformOrigin:'top center'}} variants={tassel}/></m.g>;
  // Avijit07x/animateicons: the liquid counters the flask's finite tilt.
  case 'learning':return <m.g data-animated-part="flask" style={{transformBox:'view-box',originX:'12px',originY:'22px'}} variants={flask}><path d="M14 2v6a2 2 0 0 0 .245.96l5.51 10.08A2 2 0 0 1 18 22H6a2 2 0 0 1-1.755-2.96l5.51-10.08A2 2 0 0 0 10 8V2"/><m.path data-animated-part="liquid" d="M6.453 15h11.094" style={{transformBox:'view-box',originX:'12px',originY:'15px'}} variants={liquid}/><path d="M8.5 2h7"/></m.g>;
  case 'publish':return <><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><m.g data-animated-part="upload-arrow" variants={upload}><polyline points="17 8 12 3 7 8"/><line x1="12" x2="12" y1="3" y2="15"/></m.g></>;
  case 'templates':return <><m.path data-animated-part="code-right" d="m18 16 4-4-4-4" variants={rightCode}/><m.path data-animated-part="code-left" d="m6 8-4 4 4 4" variants={leftCode}/><m.path data-animated-part="code-slash" d="m14.5 4-5 16" style={{transformBox:'view-box',originX:'12px',originY:'12px'}} variants={slash}/></>;
  case 'teams':return <><path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><m.path data-animated-part="team-outline" d="M22 21v-2a4 4 0 0 0-3-3.87" variants={users}/><m.path data-animated-part="team-member" d="M16 3.13a4 4 0 0 1 0 7.75" variants={users}/></>;
  case 'saved':return <m.g data-animated-part="bookmark" style={{transformBox:'fill-box',transformOrigin:'center'}} variants={bookmark}><path d="M17 3a2 2 0 0 1 2 2v15a1 1 0 0 1-1.496.868l-4.512-2.578a2 2 0 0 0-1.984 0l-4.512 2.578A1 1 0 0 1 5 20V5a2 2 0 0 1 2-2z"/></m.g>;
  case 'help':return <><circle cx="12" cy="12" r="10"/><m.g data-animated-part="question" style={{transformOrigin:'12px 12px'}} variants={help}><path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"/><path d="M12 17h.01"/></m.g></>;
  case 'workspace':return <>{[[3,3,7,9],[14,3,7,5],[14,12,7,9],[3,16,7,5]].map(([x,y,width,height],i)=><m.rect key={i} data-animated-part={`tile-${i}`} x={x} y={y} width={width} height={height} rx="1" custom={i} variants={tiles} style={{transformBox:'fill-box',originX:'50%',originY:'50%'}}/>)}</>;
  case 'signin':return <><m.g data-animated-part="sign-in-arrow" variants={arrow}><path d="m10 17 5-5-5-5"/><path d="M15 12H3"/></m.g><path d="M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-4"/></>;
  case 'search':return <m.g data-animated-part="lens" style={{transformOrigin:'11px 11px'}} variants={lens}><circle cx="11" cy="11" r="8"/><path d="m21 21-4.34-4.34"/></m.g>;
  case 'briefcase':return <><rect x="3" y="7" width="18" height="14" rx="2"/><m.path data-animated-part="handle" d="M8 7V5a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" variants={upload}/><path d="M3 13h18m-11 0v2h4v-2"/></>;
  case 'shield':return <><path d="M12 3 4 6v5c0 5 3 8 8 10 5-2 8-5 8-10V6l-8-3Z"/><m.path data-animated-part="check" d="m8.5 12 2.5 2.5 4.5-5" variants={draw}/></>;
  case 'recruitment':return <><circle cx="9" cy="7" r="3"/><path d="M3 20v-2a5 5 0 0 1 5-5h2a5 5 0 0 1 5 5v2"/><m.path data-animated-part="add" d="M19 8v6m-3-3h6" style={{transformOrigin:'19px 11px'}} variants={tiles} custom={0}/></>;
  case 'delivery':return <><rect x="4" y="5" width="16" height="15" rx="2"/><path d="M4 10h16m-11-5v5m6-5v5"/><m.path data-animated-part="confirmation" d="m9 15 2 2 4-4" variants={draw}/></>;
  case 'activity':return <><path d="M4 4v16h16"/><m.path data-animated-part="pulse" d="m6 14 3-4 3 6 3-9 4 4" variants={draw}/></>;
  case 'menu':return <>{[6,12,18].map((y,i)=><m.path key={y} data-animated-part={`menu-bar-${i}`} d={`M4 ${y}h16`} variants={bars([.88,.74,.6][i],i*.05)} style={{transformBox:'view-box',originX:'20px',originY:`${y}px`}}/>)}</>;
 }
}

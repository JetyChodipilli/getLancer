import {Blocks,Bookmark,CircleHelp,Code2,FlaskConical,GraduationCap,House,LayoutDashboard,LogIn,Menu,Search,Upload,Users} from 'lucide-react';

const icons={home:House,components:Blocks,college:GraduationCap,learning:FlaskConical,publish:Upload,templates:Code2,teams:Users,saved:Bookmark,help:CircleHelp,workspace:LayoutDashboard,signin:LogIn,search:Search,menu:Menu};
export type NavIconName=keyof typeof icons;

// Labels belong to the surrounding link/button, so symbols never repeat them.
export default function NavIcon({name}:{name:NavIconName}){
 const Icon=icons[name];
 return <Icon className="paper-nav-icon" data-nav-icon={name} size={18} strokeWidth={1.7} aria-hidden="true" focusable="false"/>;
}

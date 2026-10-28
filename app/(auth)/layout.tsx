import IcyAuthShell from '../components/icy-auth-shell';
import {backendOrigin} from '@/lib/server';
export default async function Layout({children}:{children:React.ReactNode}){return <IcyAuthShell preview={!(await backendOrigin())}>{children}</IcyAuthShell>}

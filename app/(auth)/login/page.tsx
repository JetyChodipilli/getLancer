import {AuthForm} from '../../components/auth-form';
import {backendOrigin} from '@/lib/server';
export default async function Page(){return <AuthForm preview={!(await backendOrigin())}/>}

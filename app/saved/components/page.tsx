import { backendOrigin } from '@/lib/server';
import SavedComponents from '@/app/components/saved-components';
export default async function Page() { return <SavedComponents preview={!(await backendOrigin())}/>; }

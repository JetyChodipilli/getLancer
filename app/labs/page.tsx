import LabWorkbench from '@/components/labs/lab-workbench';
import ArtifactObservation from '@/app/components/artifact-observation';
import {backendOrigin} from '@/lib/server';
export const metadata={title:'Lab workbench',description:'Free reviewed source, certified isolated lab execution and honest run availability.'};
export default async function Page(){return <>{await backendOrigin()&&<ArtifactObservation event="lab_discovery"/>}<LabWorkbench/></>;}

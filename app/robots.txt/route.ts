import {seoSettings} from '@/lib/seo';
export async function GET() {
  const {base,index}=await seoSettings();
  const text=index?'User-agent: *\nAllow: /\nDisallow: /workspace\nDisallow: /login\nDisallow: /signup\nDisallow: /confirm\nDisallow: /inquiry\nDisallow: /saved\nDisallow: /report\nDisallow: /preview\nDisallow: /api/\nAllow: /api/v1/media/\nSitemap: '+base+'/sitemap.xml\n':'User-agent: *\nDisallow: /\n';
  return new Response(text,{headers:{'Content-Type':'text/plain; charset=utf-8'}});
}

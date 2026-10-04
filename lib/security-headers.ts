// Provider hosts are enabled only for connected pages. Checkout loads on user intent.
export function frontendCsp(connected:boolean) {
 const payment=connected?' https://checkout.razorpay.com https://api.razorpay.com':'';
 return `default-src 'self'; script-src 'self' 'unsafe-inline'${connected?' https://checkout.razorpay.com':''}; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self'; connect-src 'self'${payment}; frame-src 'self' https://www.youtube-nocookie.com https://player.vimeo.com https://www.loom.com${payment}; object-src 'none'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'`;
}

/** Apply the same protections to HTML, API and image-optimizer responses. */
export function secureFrontendResponse(response:Response,requestUrl:string,connected:boolean) {
 const secured=new Response(response.body,response);
 secured.headers.set('X-Content-Type-Options','nosniff');
 secured.headers.set('Referrer-Policy','no-referrer');
 secured.headers.set('Permissions-Policy','camera=(), microphone=(), geolocation=()');
 secured.headers.set('Content-Security-Policy',frontendCsp(connected));
 if(new URL(requestUrl).protocol==='https:')secured.headers.set('Strict-Transport-Security','max-age=31536000');
 return secured;
}

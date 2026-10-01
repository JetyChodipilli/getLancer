// Provider hosts are enabled only for connected pages. Checkout loads on user intent.
export function frontendCsp(connected:boolean) {
 const payment=connected?' https://checkout.razorpay.com https://api.razorpay.com':'';
 return `default-src 'self'; script-src 'self' 'unsafe-inline'${connected?' https://checkout.razorpay.com':''}; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self'; connect-src 'self'${payment}; frame-src https://www.youtube-nocookie.com https://player.vimeo.com${payment}; object-src 'none'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'`;
}

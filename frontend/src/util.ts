export const getWsBasePath = () => {
   return 'ws://'+ window.location.hostname+ ":" + window.location.port  +'/ws';
};
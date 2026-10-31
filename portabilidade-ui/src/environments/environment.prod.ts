export const environment = {
  production: true,
  /**
   * Em produção o nginx da imagem serve a UI e faz proxy de /bff/ para o BFF,
   * mantendo o browser na mesma origem (sem CORS e sem URL fixa de host).
   */
  bffBaseUrl: '/bff/v1',
};

import { EnvironmentProviders, makeEnvironmentProviders } from '@angular/core';
import { environment } from '../../environments/environment';

/** Token de configuração injetável (padrão injeção Angular 17+ sem módulos). */
export const ENVIRONMENT = {
  provide: 'ENVIRONMENT',
  useValue: environment,
};

export function provideEnvironment(): EnvironmentProviders {
  return makeEnvironmentProviders([{ provide: 'ENVIRONMENT', useValue: environment }]);
}

export { environment };

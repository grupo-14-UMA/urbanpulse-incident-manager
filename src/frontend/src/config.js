const apiUrl = process.env.EXPO_PUBLIC_API_URL;

if (!apiUrl) {
  console.warn(
    'EXPO_PUBLIC_API_URL no está definida. Copia src/frontend/.env.example a src/frontend/.env, ' +
      'pon la IP de tu portátil en la red local y reinicia `npx expo start`.',
  );
}

export const API_URL = apiUrl;

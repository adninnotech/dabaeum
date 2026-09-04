// pm2 구동 정의. 배치 디렉터리에서:
//   pm2 start ecosystem.config.cjs
//   pm2 logs daeguchain-emulator
//   pm2 restart daeguchain-emulator
// .env 는 코드(dotenv)가 읽으므로 여기서는 환경변수를 정의하지 않는다.
module.exports = {
  apps: [
    {
      name: 'daeguchain-emulator',
      script: 'dist/main.js',
      cwd: __dirname,
      instances: 1,
      exec_mode: 'fork',
      autorestart: true,
      max_restarts: 10,
      restart_delay: 3000,
      env: { NODE_ENV: 'production' },
      out_file: 'logs/out.log',
      error_file: 'logs/error.log',
      merge_logs: true,
      time: true,
    },
  ],
};

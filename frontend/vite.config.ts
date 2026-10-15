import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
const proxy={'/api':{target:process.env.BACKEND_URL||'http://127.0.0.1:8080'},'/actuator':{target:process.env.BACKEND_URL||'http://127.0.0.1:8080'}};
export default defineConfig({plugins:[react()],server:{port:5173,strictPort:true,proxy},preview:{strictPort:true,proxy},build:{rolldownOptions:{output:{strictExecutionOrder:true,codeSplitting:{groups:[{name:'react-vendor',test:/node_modules\/(react|react-dom|scheduler)\//},{name:'ui-vendor',maxSize:250000,test:/node_modules\/(@ant-design|antd|rc-|@rc-component)/}]}}}}});

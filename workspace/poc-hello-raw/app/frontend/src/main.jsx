import { StrictMode, useEffect, useState } from 'react';
import { createRoot } from 'react-dom/client';

function App() {
  const [data, setData] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => {
    fetch('/api/hello')
      .then((r) => r.json())
      .then(setData)
      .catch(() => setError('백엔드(8081)에 연결할 수 없습니다. run-backend.bat 를 먼저 실행하세요.'));
  }, []);

  return (
    <div style={{ fontFamily: 'sans-serif', textAlign: 'center', marginTop: 80 }}>
      <h1>React + Spring Boot Hello</h1>
      {error && <p style={{ color: 'crimson' }}>{error}</p>}
      {data ? (
        <>
          <p style={{ fontSize: 28, fontWeight: 700 }}>{data.message}</p>
          <p style={{ color: '#666' }}>서버 응답 시각: {data.time}</p>
        </>
      ) : (
        !error && <p>백엔드에서 응답을 기다리는 중...</p>
      )}
    </div>
  );
}

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <App />
  </StrictMode>,
);

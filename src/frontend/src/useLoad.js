import { useCallback, useEffect, useRef, useState } from 'react';

// Shared state machine for read screens. A reload keeps the previous data so a
// short outage never turns useful content into a blank screen.
export function useLoad(loader) {
  const loaderRef = useRef(loader);
  const requestIdRef = useRef(0);
  const dataRef = useRef(undefined);
  const [state, setState] = useState({ status: 'loading', data: undefined, error: null });

  useEffect(() => {
    loaderRef.current = loader;
  }, [loader]);

  const reload = useCallback(() => {
    const requestId = ++requestIdRef.current;
    setState((current) => ({ ...current, status: 'loading', error: null }));

    Promise.resolve()
      .then(() => loaderRef.current())
      .then((data) => {
        if (requestId !== requestIdRef.current) return;
        dataRef.current = data;
        setState({ status: 'loaded', data, error: null });
      })
      .catch((error) => {
        if (requestId !== requestIdRef.current) return;
        const status = error?.status === 404 ? 'notFound' : dataRef.current === undefined ? 'error' : 'loaded';
        setState((current) => ({
          status,
          data: current.data,
          error,
        }));
      });
  }, []);

  useEffect(() => {
    Promise.resolve().then(reload);
    return () => {
      requestIdRef.current += 1;
    };
  }, [reload]);

  return { ...state, reload };
}

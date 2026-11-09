import { useState } from 'react';
import { ActivityIndicator, Pressable, ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';

import { login, register } from './api';
import { useAuth } from './AuthContext';
import { COLORS } from './theme';

function fieldErrors(error) {
  const errors = error?.payload?.errors;
  return errors && typeof errors === 'object' ? errors : {};
}

function validateLogin({ email, password }) {
  const errors = {};
  if (!email.trim()) errors.email = 'Introduce tu email.';
  if (!password) errors.password = 'Introduce tu contraseña.';
  return errors;
}

function validateRegistration({ name, email, password }) {
  const errors = validateLogin({ email, password });
  if (!name.trim()) errors.name = 'Introduce tu nombre.';
  return errors;
}

export function AuthenticationScreen({ initialNotice }) {
  const { completeAuthentication } = useAuth();
  const [mode, setMode] = useState('login');
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [errors, setErrors] = useState({});
  const [submitError, setSubmitError] = useState(initialNotice ?? null);
  const [sending, setSending] = useState(false);

  const isRegistration = mode === 'register';

  function switchMode() {
    setMode((current) => (current === 'login' ? 'register' : 'login'));
    setErrors({});
    setSubmitError(null);
  }

  async function submit() {
    const values = { name, email, password };
    const validationErrors = isRegistration ? validateRegistration(values) : validateLogin(values);
    if (Object.keys(validationErrors).length > 0) {
      setErrors(validationErrors);
      return;
    }

    setSending(true);
    setErrors({});
    setSubmitError(null);
    try {
      const response = isRegistration
        ? await register({ name, email, password })
        : await login({ email, password });
      await completeAuthentication(response);
    } catch (error) {
      setErrors(fieldErrors(error));
      setSubmitError(error?.message || 'No se pudo completar la solicitud. Inténtalo de nuevo.');
    } finally {
      setSending(false);
    }
  }

  return (
    <ScrollView contentContainerStyle={styles.container} keyboardShouldPersistTaps="handled">
      <View style={styles.card}>
        <Text style={styles.title}>{isRegistration ? 'Crear cuenta' : 'Iniciar sesión'}</Text>
        <Text style={styles.subtitle}>
          {isRegistration ? 'Crea una cuenta para registrar incidencias.' : 'Accede para consultar y registrar incidencias.'}
        </Text>

        {isRegistration && (
          <Field label="Nombre" value={name} onChangeText={setName} error={errors.name} editable={!sending} />
        )}
        <Field
          label="Email"
          value={email}
          onChangeText={setEmail}
          error={errors.email}
          editable={!sending}
          keyboardType="email-address"
          autoCapitalize="none"
          autoComplete="email"
        />
        <Field
          label="Contraseña"
          value={password}
          onChangeText={setPassword}
          error={errors.password}
          editable={!sending}
          secureTextEntry
          autoCapitalize="none"
          autoComplete={isRegistration ? 'new-password' : 'current-password'}
        />

        {submitError && <Text style={styles.submitError}>{submitError}</Text>}
        <Pressable
          style={({ pressed }) => [styles.submitButton, (pressed || sending) && styles.submitButtonPressed]}
          onPress={submit}
          disabled={sending}
          accessibilityRole="button"
          accessibilityState={{ busy: sending, disabled: sending }}
        >
          {sending ? <ActivityIndicator color={COLORS.onPrimary} /> : <Text style={styles.submitText}>{isRegistration ? 'Crear cuenta' : 'Iniciar sesión'}</Text>}
        </Pressable>
        <Pressable onPress={switchMode} disabled={sending} accessibilityRole="button">
          <Text style={styles.switchText}>{isRegistration ? 'Ya tengo una cuenta' : 'Crear cuenta'}</Text>
        </Pressable>
      </View>
    </ScrollView>
  );
}

function Field({ label, error, ...inputProps }) {
  return (
    <View style={styles.field}>
      <Text style={styles.label}>{label}</Text>
      <TextInput style={[styles.input, error && styles.inputError]} placeholder={label} {...inputProps} />
      {error && <Text style={styles.error}>{error}</Text>}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flexGrow: 1, justifyContent: 'center', padding: 20, backgroundColor: COLORS.surface },
  card: { width: '100%', maxWidth: 420, alignSelf: 'center' },
  title: { color: COLORS.text, fontSize: 28, fontWeight: '700', textAlign: 'center' },
  subtitle: { marginTop: 10, color: COLORS.textMuted, fontSize: 16, lineHeight: 22, textAlign: 'center' },
  field: { marginTop: 20 },
  label: { marginBottom: 6, color: COLORS.text, fontSize: 15, fontWeight: '600' },
  input: { borderWidth: 1, borderColor: COLORS.border, borderRadius: 8, paddingHorizontal: 12, paddingVertical: 11, color: COLORS.text, fontSize: 16 },
  inputError: { borderColor: COLORS.error },
  error: { marginTop: 5, color: COLORS.error, fontSize: 13 },
  submitError: { marginTop: 18, color: COLORS.error, fontSize: 14, lineHeight: 20, textAlign: 'center' },
  submitButton: { marginTop: 24, borderRadius: 8, paddingVertical: 13, alignItems: 'center', backgroundColor: COLORS.primary },
  submitButtonPressed: { backgroundColor: COLORS.primaryPressed },
  submitText: { color: COLORS.onPrimary, fontSize: 16, fontWeight: '600' },
  switchText: { marginTop: 20, color: COLORS.primary, fontSize: 16, fontWeight: '600', textAlign: 'center' },
});

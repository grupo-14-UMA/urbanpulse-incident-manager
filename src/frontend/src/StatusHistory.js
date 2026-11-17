import { ActivityIndicator, Pressable, StyleSheet, Text, View } from 'react-native';

import { describeStatusChange } from './statusHistory';
import { COLORS } from './theme';

// Vertical timeline of every status change of an incident, oldest first.
// Presentational: its load state comes from props, so a failed history request
// never hides the incident detail and can be retried on its own.
export function StatusHistory({ changes, loading = false, error = null, onRetry }) {
  const entries = Array.isArray(changes) ? changes.map(describeStatusChange) : [];

  return (
    <View>
      <Text style={styles.label}>Historial de estados</Text>
      {entries.length === 0 && loading && (
        <ActivityIndicator color={COLORS.primary} style={styles.loading} />
      )}
      {entries.length === 0 && !loading && error && (
        <View style={styles.failure}>
          <Text style={styles.failureText}>{error?.message || 'No se pudo cargar el historial.'}</Text>
          <Pressable onPress={onRetry} accessibilityRole="button">
            <Text style={styles.retryText}>Reintentar</Text>
          </Pressable>
        </View>
      )}
      {entries.length === 0 && !loading && !error && (
        <Text style={styles.empty}>Todavía no hay cambios registrados.</Text>
      )}
      {entries.length > 0 && (
        <View style={styles.timeline}>
          {entries.map((entry, index) => (
            <View key={index} style={styles.entry}>
              <View style={styles.marker}>
                <View style={[styles.dot, entry.isRegistration && styles.dotRegistered]} />
                {index < entries.length - 1 && <View style={styles.line} />}
              </View>
              <View style={styles.body}>
                <Text style={styles.heading}>{entry.heading}</Text>
                {entry.date ? <Text style={styles.date}>{entry.date}</Text> : null}
                {entry.note && (
                  <Text style={[styles.note, styles[entry.note.kind]]}>
                    {entry.note.label}: {entry.note.text}
                  </Text>
                )}
              </View>
            </View>
          ))}
        </View>
      )}
      {entries.length > 0 && error && (
        <View style={styles.failure}>
          <Text style={styles.failureText}>{error?.message || 'No se pudo cargar el historial.'}</Text>
          <Pressable onPress={onRetry} accessibilityRole="button">
            <Text style={styles.retryText}>Reintentar</Text>
          </Pressable>
        </View>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  label: {
    marginTop: 16,
    marginBottom: 4,
    fontSize: 13,
    fontWeight: '600',
    color: COLORS.textMuted,
    textTransform: 'uppercase',
  },
  loading: {
    marginTop: 8,
  },
  empty: {
    marginTop: 4,
    fontSize: 16,
    color: COLORS.text,
  },
  timeline: {
    marginTop: 4,
  },
  entry: {
    flexDirection: 'row',
  },
  marker: {
    width: 24,
    alignItems: 'center',
  },
  dot: {
    width: 10,
    height: 10,
    borderRadius: 5,
    marginTop: 6,
    backgroundColor: COLORS.primary,
  },
  dotRegistered: {
    backgroundColor: COLORS.primaryPressed,
  },
  line: {
    width: 2,
    flex: 1,
    backgroundColor: COLORS.border,
  },
  body: {
    flex: 1,
    paddingLeft: 10,
    paddingBottom: 16,
  },
  heading: {
    fontSize: 16,
    fontWeight: '600',
    color: COLORS.text,
  },
  date: {
    marginTop: 2,
    fontSize: 14,
    color: COLORS.textMuted,
  },
  note: {
    marginTop: 4,
    fontSize: 14,
  },
  rejection: {
    color: COLORS.error,
  },
  resolution: {
    color: COLORS.success,
  },
  neutral: {
    color: COLORS.textMuted,
  },
  failure: {
    marginTop: 8,
    padding: 12,
    borderRadius: 8,
    backgroundColor: COLORS.errorSoft,
  },
  failureText: {
    color: COLORS.error,
    textAlign: 'center',
  },
  retryText: {
    marginTop: 6,
    textAlign: 'center',
    color: COLORS.primary,
    fontWeight: '600',
  },
});

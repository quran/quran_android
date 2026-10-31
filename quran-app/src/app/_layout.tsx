import { AmiriQuran_400Regular, useFonts } from '@expo-google-fonts/amiri-quran';
import { Stack } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import * as SystemUI from 'expo-system-ui';
import { useEffect } from 'react';
import { ActivityIndicator, View } from 'react-native';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { hydrate, useStore } from '../state/store';
import { usePalette } from '../theme';

export default function RootLayout() {
  const [fontsLoaded] = useFonts({ AmiriQuran_400Regular });
  const hydrated = useStore((s) => s.hydrated);
  const p = usePalette();

  useEffect(() => {
    hydrate();
  }, []);

  useEffect(() => {
    SystemUI.setBackgroundColorAsync(p.bg).catch(() => {});
  }, [p.bg]);

  return (
    <SafeAreaProvider>
      <StatusBar style={p.statusBar} />
      {!fontsLoaded || !hydrated ? (
        <View style={{ flex: 1, backgroundColor: p.bg, alignItems: 'center', justifyContent: 'center' }}>
          <ActivityIndicator color={p.accent} />
        </View>
      ) : (
        <Stack
          screenOptions={{
            headerShown: false,
            contentStyle: { backgroundColor: p.bg },
            animation: 'slide_from_right',
          }}
        />
      )}
    </SafeAreaProvider>
  );
}

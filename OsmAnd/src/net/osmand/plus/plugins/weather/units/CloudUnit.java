package net.osmand.plus.plugins.weather.units;

import android.content.Context;

import androidx.annotation.NonNull;

public enum CloudUnit implements WeatherUnit {

	PERCENT("%");

	private final String symbol;

	CloudUnit(@NonNull String symbol) {
		this.symbol = symbol;
	}

	@NonNull
	@Override
	public String getSymbol() {
		return symbol;
	}

	@Override
	public String toHumanString(@NonNull Context ctx) {
		return symbol;
	}
}
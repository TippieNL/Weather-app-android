import { sql } from "drizzle-orm";
import { pgTable, text, varchar } from "drizzle-orm/pg-core";
import { createInsertSchema } from "drizzle-zod";
import { z } from "zod";

export const users = pgTable("users", {
  id: varchar("id").primaryKey().default(sql`gen_random_uuid()`),
  username: text("username").notNull().unique(),
  password: text("password").notNull(),
});

export const insertUserSchema = createInsertSchema(users).pick({
  username: true,
  password: true,
});

export type InsertUser = z.infer<typeof insertUserSchema>;
export type User = typeof users.$inferSelect;

export const weatherConditions = [
  "clear",
  "cloudy", 
  "rainy",
  "stormy",
  "snowy",
  "foggy",
  "windy",
  "hot",
  "cold"
] as const;

export type WeatherCondition = typeof weatherConditions[number];

export const weatherDataSchema = z.object({
  condition: z.enum(weatherConditions),
  isDay: z.boolean(),
  temperature: z.number(),
  description: z.string(),
  location: z.string(),
  funnyQuote: z.string(),
  subtitle: z.string(),
  feelsLike: z.number(),
  temperatureMax: z.number(),
  temperatureMin: z.number(),
  humidity: z.number(),
  precipitationChance: z.number(),
  windSpeed: z.number(),
  uvIndex: z.number(),
  pressure: z.number(),
  dailyForecast: z.array(z.object({ day: z.string(), date: z.string(), temperatureMax: z.number(), temperatureMin: z.number() })),
  hourlyForecast: z.array(z.object({ time: z.string(), temperature: z.number(), precipitationChance: z.number().optional() })),
});

export type WeatherData = z.infer<typeof weatherDataSchema>;

export const locationSchema = z.object({
  latitude: z.number(),
  longitude: z.number(),
});

export type Location = z.infer<typeof locationSchema>;

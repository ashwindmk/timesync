package me.tatarka.timesync.lib;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class TimeSyncParser {
    private static Map<String, TimeSync> sListeners;

    static Map<String, TimeSync> parseListeners(Context context) {
        if (sListeners != null) {
            return sListeners;
        }

        sListeners = new HashMap<>();

        try {
            XmlPullParser parser = context.getResources().getXml(getResource(context));
            while (parser.getEventType() != XmlPullParser.END_DOCUMENT) {
                if (parser.getEventType() == XmlPullParser.START_TAG) {
                    if (parser.getName().equals("listener")) {
                        String className = parser.getAttributeValue(null, "name");
                        if (className == null) {
                            throw new IllegalArgumentException("Your <listener/> must have the attribute android:name=\"[CLASS_NAME]\"");
                        }
                        if (className.startsWith(".")) {
                            className = context.getPackageName() + className;
                        }

                        TimeSync listener = createListener(className);
                        listener.onCreate(context);
                        listener.ensureOnCreate();

                        List<TimeSync.Edit> edits = new ArrayList<>();

                        String enabledString = parser.getAttributeValue(null, "enabled");
                        if (enabledString != null) {
                            edits.add(TimeSync.Edit.enable(validatingParseBoolean(enabledString)));
                        }

                        String everyString = parser.getAttributeValue(null, "every");
                        if (everyString != null) {
                            edits.add(TimeSync.Edit.every(parseUnitTimeSpan(everyString)));
                        }

                        String rangeString = parser.getAttributeValue(null, "range");
                        if (rangeString != null) {
                            edits.add(TimeSync.Edit.range(parseUnitTimeSpan(rangeString)));
                        }

                        listener.editDefault(edits);

                        sListeners.put(listener.getName(), listener);
                    }
                }
                parser.next();
            }
            return sListeners;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (XmlPullParserException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static TimeSync createListener(String name) throws IllegalArgumentException {
        try {
            return (TimeSync) Class.forName(name).newInstance();
        } catch (InstantiationException | IllegalAccessException | ClassNotFoundException | ClassCastException e) {
            throw new IllegalArgumentException("Invalid TimeSync {" + name + "}", e);
        }
    }

    private static int getResource(Context context) {
        try {
            ApplicationInfo appInfo = context.getPackageManager().getApplicationInfo(context.getPackageName(), PackageManager.GET_META_DATA);
            int res = appInfo.metaData.getInt(TimeSync.META_DATA_NAME);
            if (res == 0) {
                throw new IllegalArgumentException("You must declare <meta-data android:name=\"" + TimeSync.META_DATA_NAME + "\" android:resource=\"@xml/[RESOURCE_NAME]\"/> in your AndroidManifest.xml");
            }
            return res;
        } catch (PackageManager.NameNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean validatingParseBoolean(String input) throws BooleanFormatException {
        switch (input) {
            case "true":
                return true;
            case "false":
                return false;
            default:
                throw new BooleanFormatException("For input string: " + input);
        }
    }

    private static final Pattern timeSpanRegex = Pattern.compile("(\\d+) +(second|minute|hour|day|week)s?");
    private static long parseUnitTimeSpan(String input) throws IllegalArgumentException {
        if (input == null) {
            throw new IllegalArgumentException("For input string: " + input);
        }

        Matcher matcher = timeSpanRegex.matcher(input);
        if (matcher.matches()) {
            String timeSpanString = matcher.group(1);
            long timeUnit = Long.parseLong(timeSpanString);
            String unitString = matcher.group(2);

            switch (unitString) {
                case "second": return timeUnit * TimeSync.Config.SECONDS;
                case "minute": return timeUnit * TimeSync.Config.MINUTES;
                case "hour": return timeUnit * TimeSync.Config.HOURS;
                case "day": return timeUnit * TimeSync.Config.DAYS;
                case "week": return timeUnit * TimeSync.Config.WEEKS;
                default:throw new IllegalArgumentException("Unknown unit: " + unitString);
            }
        } else {
            // May have no units, just parse as milliseconds
            return Long.parseLong(input);
        }
    }
}

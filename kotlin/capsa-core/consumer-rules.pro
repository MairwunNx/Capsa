# Keeps the class names of facts and effects in apps minified with R8.
# CapsaViolationException and the runtime history print them, so a crash report shows which fact led to the failure.
-keepnames class * implements com.mairwunnx.capsa.Fact
-keepnames class * implements com.mairwunnx.capsa.Effect

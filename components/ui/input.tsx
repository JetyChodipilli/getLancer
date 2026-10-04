import * as React from "react"

import { cn } from "@/lib/utils"

function Input({ className, type, ...props }: React.ComponentProps<"input">) {
  const nativeGeometry = ["checkbox", "radio", "range", "color", "hidden"].includes(type ?? "")

  return (
    <input
      type={type}
      data-slot="input"
      className={cn(
        "min-w-0 text-base text-foreground transition-[border-color] placeholder:text-muted-foreground disabled:cursor-not-allowed selection:bg-primary selection:text-primary-foreground aria-invalid:border-destructive",
        nativeGeometry
          ? "accent-primary"
          : "min-h-11 w-full rounded-xl border border-input bg-background px-3 py-2 file:mr-3 file:rounded-md file:border-0 file:bg-secondary file:px-3 file:py-1 file:text-base file:font-medium file:text-foreground",
        "focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring",
        className
      )}
      {...props}
    />
  )
}

export { Input }
